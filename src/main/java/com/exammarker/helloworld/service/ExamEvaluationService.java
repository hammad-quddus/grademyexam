package com.exammarker.helloworld.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel; // Swapped to generic interface
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.dto.QuestionEvaluationDto;
import com.exammarker.helloworld.dto.rubric.RubricDto;
import com.exammarker.helloworld.dto.solution.TranscribedSolutionsDto;
import com.exammarker.helloworld.dto.studentpaper.TranscribedExamDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class ExamEvaluationService {

	private static final Logger log = LoggerFactory.getLogger(ExamEvaluationService.class); // Fixed target logger class

	private final ChatModel chatModel; // Swapped to interface type

	private final PdfAssemblyService pdfAssemblyService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ExamEvaluationService(ChatModel chatModel, PdfAssemblyService pdfAssemblyService) { // Fixed injection constructor
		this.chatModel = chatModel;
		this.pdfAssemblyService = pdfAssemblyService;
	}


	
	
	public QuestionEvaluationDto evaluateQuestion(List<MultipartFile> paperImages, List<MultipartFile> rubricImages,
			List<MultipartFile> solutionImages) throws Exception {

		byte[] paperPdfBytes = pdfAssemblyService.imagesToPdf(paperImages);
		byte[] rubricPdfBytes = pdfAssemblyService.imagesToPdf(rubricImages);
		byte[] solutionPdfBytes = pdfAssemblyService.imagesToPdf(solutionImages);

		Resource studentWorkPdf = new ByteArrayResource(paperPdfBytes);
		Resource solutionsPdf = new ByteArrayResource(solutionPdfBytes);
		Resource rubricPdf = new ByteArrayResource(rubricPdfBytes);

		SystemMessage systemMessage = new SystemMessage(
				"""
						        You are an experienced 9th-grade Islamic Studies teacher.

						        Read ALL attached files carefully.

						Tasks:
						1. Read the rubric
						2. Read the exam solutions
						3. Read the student's handwritten paper
						4. Transcribe the student answers
						5. Compare against solutions
						6. Assign marks out of 10
						7. Identify key expected points that are missing or insufficiently addressed in the student's answer (coverage gaps)

						Rules:
						- Never invent student answers
						- If handwriting is unreadable, explicitly say so
						- Base grading on the supplied rubric
						- Be strict but fair
						- Coverage gaps must strictly come from rubric/official solutions (do not hallucinate new expectations)
						- Return ONLY valid JSON
						- Do not return markdown
						- Do not wrap JSON in triple backticks
						- The uploaded pages of the student paper may be out of order; use content continuity and context to infer the correct sequence where necessary before grading.

						JSON schema:
						{
						  "studentName": string | null,
						  "questionId": "string",
						  "questionText": string,
						  "maxMarks": integer,
						  "marksAwarded": integer,
						  "studentAnswerTranscription": string,

						  "officialSolutionKeyPoints": [
						    string
						  ],

						  "coverageGaps": [
						    string
						  ],

						  "evaluation": {
						    "accuracy": [
						      string
						    ],
						    "coverage": [
						      string
						    ],
						    "useOfResources": [
						      string
						    ],
						    "structure": [
						      string
						    ],
						    "relevance": [
						      string
						    ]
						  },

						  "evaluationSummary": string,

						  "strengths": [
						    string
						  ],

						  "improvements": [
						    string
						  ],

						  "factualErrors": [
						    string
						  ],

						  "teacherComments": [
						    string
						  ],

						  "rubricReference": {
						    "band": {
						      "min": integer,
						      "max": integer
						    },
						    "descriptor": string
						  },

						  "confidence": {
						    "transcriptionConfidence": number,
						    "gradingConfidence": number
						  },

						  "requiresHumanReview": boolean
						}
						""");

		UserMessage rubricMessage = UserMessage.builder().text("This is the grading rubric.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), rubricPdf)).build();

		UserMessage solutionsMessage = UserMessage.builder().text("These are the official exam solutions.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), solutionsPdf)).build();

		UserMessage studentMessage = UserMessage.builder().text("""
				This is the student's handwritten exam paper.

				Please:
				- transcribe the student's answer carefully
				- identify unclear or unreadable handwriting
				- compare the answer against the supplied marking scheme
				- evaluate the answer using the rubric
				- extract supporting evidence directly from the student's writing
				- assign marks fairly and accurately
				- return ONLY valid JSON matching the required schema
				""").media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), studentWorkPdf)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, rubricMessage, solutionsMessage, studentMessage));

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (Exception e) {
			throw new RuntimeException("AI grading failed", e);
		}

		var raw = response.getResult().getOutput().getText();

		log.info("====== Response from ai model: ========");
		log.info(raw);

		//objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		//QuestionEvaluationDto dto = objectMapper.readValue(raw, QuestionEvaluationDto.class);

		BeanOutputConverter<QuestionEvaluationDto> converter = new BeanOutputConverter<>(QuestionEvaluationDto.class);
		// 3. Let the converter safely translate the output natively
	    QuestionEvaluationDto dto = converter.convert(raw);

		validate(dto);

		return dto;
	}

	////////

	public RubricDto transcribeRubric(List<MultipartFile> rubricImages) throws Exception {

		byte[] rubricPdfBytes = pdfAssemblyService.imagesToPdf(rubricImages);
		Resource rubricPdf = new ByteArrayResource(rubricPdfBytes);

		return transcribeRubric(rubricPdf);
	}

	public RubricDto transcribeRubric(Resource rubricPdf) throws Exception {

		SystemMessage systemMessage = new SystemMessage("""
				You are a rubric transcription engine.

				TASK:
				Extract and normalize the rubric from the attached PDF into the provided JSON schema.

				STRICT RULES:
				- Only extract information explicitly present in the rubric.
				- Do NOT infer missing grading structures.
				- Do NOT rewrite or reinterpret descriptors.
				- Preserve rubric meaning as closely as possible.

				- Rubric categories may apply to multiple questions or question types.
				- Preserve assessment objective groupings if present (e.g. AO1, AO2).
				- Do not convert rubric categories into individual exam questions.


				QUESTION MAPPING RULES:
				- Each question must be represented individually.
				- Do NOT group questions into ranges or sets.
				- questionId MUST refer to exactly ONE question.
				- questionId MUST NOT contain ranges, intervals, or multiple values (e.g., "Q2-5a" is invalid).
				- Use atomic identifiers only (e.g., "Q1a", "Q2b").
				
				- If a rubric category applies to multiple questions, repeat the mapping entry for each questionId.
				- Do NOT compress or merge question mappings.
				
				IMPORTANT:
				- Do NOT infer question grouping, numbering patterns, or implied ranges from layout or sequence.
				- Treat each visually distinct question boundary as a separate entity.
				- If uncertain, prefer over-segmentation (create more question entries rather than fewer).
				- If a question boundary is unclear, still create a separate entry rather than merging.																								

				If information is unclear or unreadable:
				- set field to null

				Ignore:
				- formatting artifacts
				- OCR noise
				- repeated text
				- layout inconsistencies


				OUTPUT:
				Must strictly follow JSON schema.
				No extra fields.
				No commentary.
				
				JSON Schema:
				
				{
				  "rubricId": "string",
				  "subject": "string",
				
				  "rubricCategories": [
				    {
				      "rubricCategoryId": "string",
				      "assessmentObjective": "string",
				      "description": "string",
				      "scoringRule": "best-fit",
				
				      "levels": [
				        {
				          "levelId": "string",
				          "levelNumber": 0,
				
				          "markRange": {
				            "min": 0,
				            "max": 0
				          },
				
				          "descriptor": "string",
				
				          "characteristics": [
				            "string"
				          ],
				
				          "evidenceKeywords": [
				            "string"
				          ]
				        }
				      ]
				    }
				  ],
				
				  "questionMappings": [
				    {
				      "questionId": "string",
				      "rubricCategoryId": "string",
				      "maxMarks": 0
				    }
				  ]
				}
				""");
		
		UserMessage rubricMessage = UserMessage.builder().text("This is the grading rubric.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), rubricPdf)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, rubricMessage));

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (Exception e) {
			throw new RuntimeException("AI parsing failed", e);
		}

		var raw = response.getResult().getOutput().getText();

		log.info("====== Response from ai model for rubric transcription: ========");
		log.info(raw);

		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		RubricDto dto = objectMapper.readValue(raw, RubricDto.class);

		return dto;
	}

	///
	/// 
	/// 
	/// 
	/// 
	/// 
	
	/**
	 * Phase 1b: Transcribes and structures out-of-order official exam solutions and marking criteria.
	 */
	public TranscribedSolutionsDto transcribeOfficialSolutions(List<MultipartFile> solutionImages) throws Exception {
		byte[] solutionPdfBytes = pdfAssemblyService.imagesToPdf(solutionImages);
		Resource solutionsPdf = new ByteArrayResource(solutionPdfBytes);

		SystemMessage systemMessage = new SystemMessage(
				"""
				You are an advanced academic OCR coordinator.
				
				Task:
				Analyze the attached Official Exam Solutions PDF. Extract, segment, and structure all question rubrics 
				and answer guidelines.
				
				Rules:
				1. Reconstruct logical solutions and sub-questions (e.g., Q3a, Q3b, Q1a, Q1b) that may span across pages.
				2. The pages in the attached PDF may be completely out of order. Use numbering, subject headings, and conceptual flow to piece them together sequentially.
				3. For each structured question block, extract:
				   - questionId: Standard identifier (e.g., Q1a, Q3b, Q4a)
				   - questionText: Full textual prompt of the question.
				   - maxMarks: Total marks assigned (e.g. 10, 4), parsed from brackets or descriptors.
				   - officialSolutionKeyPoints: Verbatim expected facts, events, historical figures, verses, or points.
				   - markingGuidelines: The guidance, instructions, or grading criteria given to examiners for this question.
				4. Return ONLY valid JSON matching the schema below. Do not wrap in markdown or backticks.
				
				JSON schema:
				{
				  "subject": "string or null",
				  "examCode": "string or null",
				  "questions": [
				    {
				      "questionId": "string (e.g., Q1a, Q3b)",
				      "questionText": "string",
				      "maxMarks": integer,
				      "officialSolutionKeyPoints": [ "string" ],
				      "markingGuidelines": "string"
				    }
				  ]
				}
				""");

		UserMessage solutionsMessage = UserMessage.builder()
				.text("Please analyze and reconstruct the structured official exam solutions.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), solutionsPdf))
				.build();

		Prompt prompt = new Prompt(List.of(systemMessage, solutionsMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		BeanOutputConverter<TranscribedSolutionsDto> converter = new BeanOutputConverter<>(TranscribedSolutionsDto.class);
		return converter.convert(rawJson);
	}
	
	
	

	/**
	 * Phase 1: Transcribes and segments handwritten student exam pages into structured question-answer pairs.
	 */
	public TranscribedExamDto transcribeAndSegmentPaper(List<MultipartFile> paperImages) throws Exception {
		byte[] paperPdfBytes = pdfAssemblyService.imagesToPdf(paperImages);
		Resource studentWorkPdf = new ByteArrayResource(paperPdfBytes);

		SystemMessage systemMessage = new SystemMessage(
				"""
				You are an advanced educational AI assistant specialized in document processing and OCR transcription.
				
				Task:
				Analyze the attached student paper PDF. Identify individual handwritten answers, transcribe them exactly, 
				and structure them logically into question-answer blocks.
				
				Rules:
				1. Reconstruct logical answers that span across page boundaries. 
				2. Pages might be out of order; match by narrative and conceptual continuity.
				3. Transcribe verbatim. If handwriting is illegible, write "[unreadable handwriting]".
				4. IGNORE CROSSED-OUT TEXT: If a student has struck through, scribbled over, crossed out, or clearly deleted any text, words, or entire paragraphs, do NOT transcribe them. Skip crossed-out content entirely from the final transcription so the evaluator does not process retracted thoughts.
				5. Attempt to capture metadata like Student Name, Student ID, Subject, Class/Section, and Date if present.
				6. Return ONLY valid JSON matching the schema below. Do not wrap in markdown or backticks.
				
				JSON schema:
				{
				  "subject": "string or null",
				  "classAndSection": "string or null",
				  "date": "string or null",
				  "studentId": "string or null",
				  "studentName": "string or null",
				  "questions": [
				    {
				      "questionId": "string (e.g., Q1, Q1a, Q2)",
				      "questionText": "string (the question being answered, inferred from context if implicit)",
				      "answerText": "string (the complete verbatim transcribed student answer)",
				      "maxMarks": integer
				    }
				  ]
				}
				""");

		UserMessage paperMessage = UserMessage.builder()
				.text("Please transcribe this multi-page student paper and group the responses logically by question.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), studentWorkPdf))
				.build();

		Prompt prompt = new Prompt(List.of(systemMessage, paperMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		log.info("====== Response from ai model for studentpaper transcription: ========");
		log.info(rawJson);

		BeanOutputConverter<TranscribedExamDto> converter = new BeanOutputConverter<>(TranscribedExamDto.class);
		return converter.convert(rawJson);
	}



	private void validate(QuestionEvaluationDto result) {
		if (result == null) {
			throw new IllegalStateException("AI returned null response");
		}

		if (result.marksAwarded() == null || result.maxMarks() == null) {
			throw new IllegalStateException("Missing marks in evaluation");
		}

		if (result.rubricReference() == null) {
			throw new IllegalStateException("Missing rubric reference");
		}
	}
}