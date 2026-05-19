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
import com.exammarker.helloworld.dto.solution.SolutionDto;
import com.exammarker.helloworld.dto.studentpaper.QuestionAnswerDto;
import com.exammarker.helloworld.dto.studentpaper.StudentPaperDto;
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

	public StudentPaperDto evaluateOneQuestionFromExam(String questionId, RubricDto rubricDto, SolutionDto solutionDto, 
			StudentPaperDto studentPaperDto) {
		
		return null;
	}
	
//////////////
/// 
	public StudentPaperDto alignStudentPaperToQuestions(
	        StudentPaperDto studentPaperDto,
	        SolutionDto solutionDto
	) {

	    try {

	        // 1. Serialize inputs to JSON (THIS is what the LLM consumes)
	        String studentJson = objectMapper.writeValueAsString(studentPaperDto);
	        String solutionJson = objectMapper.writeValueAsString(solutionDto);

	        // 2. System prompt (alignment engine)
	        SystemMessage systemMessage = new SystemMessage("""
	        		You are an exam paper alignment engine.

	        		TASK:
	        		You will receive:
	        		1. A student's exam paper (StudentPaperDto)
	        		2. An official solution structure (SolutionDto)

	        		Your job is to ALIGN each student answer to the correct official question.

	        		────────────────────────────────────
	        		CRITICAL RULES
	        		────────────────────────────────────

	        		- Do NOT grade or evaluate answers.
	        		- Do NOT modify student answerText.
	        		- Do NOT invent questionIds.
	        		- ALL questionIds MUST come from SolutionDto.questionMappings.
	        		- ALL questionText MUST come from SolutionDto.

	        		────────────────────────────────────
	        		ALIGNMENT LOGIC (priority order)
	        		────────────────────────────────────

	        		Match each student answer to the correct question using:

	        		1. Similarity of student answer to modelAnswer / keyPoints
	        		2. Presence of partial questionText in student data (if any)
	        		3. rawQuestionLabel (weak signal only)
	        		4. Order of appearance in paper (fallback only)

	        		────────────────────────────────────
	        		STRICT OUTPUT RULES
	        		────────────────────────────────────

	        		For each student question:
	        		- Assign correct questionId from SolutionDto
	        		- Attach correct questionText from SolutionDto
	        		- Keep answerText unchanged

	        		If uncertain:
	        		- still choose best matching question from SolutionDto
	        		- NEVER leave questionId null

	        		────────────────────────────────────
	        		OUTPUT FORMAT
	        		────────────────────────────────────

	        		Return a valid StudentPaperDto JSON:
	        		- subject unchanged
	        		- studentId unchanged
	        		- questions fully aligned:
	        		    - questionId populated
	        		    - questionText populated from SolutionDto
	        		    - answerText unchanged

	        		No commentary.
	        		No markdown.
	        		""");	        
	        
	        // 3. Student input
	        UserMessage studentMessage = UserMessage.builder()
	                .text("""
	                    Student Paper (transcribed JSON):
	                    """ + studentJson)
	                .build();

	        // 4. Solution input (GROUND TRUTH)
	        UserMessage solutionMessage = UserMessage.builder()
	                .text("""
	                    Official Solution Structure (JSON):
	                    """ + solutionJson)
	                .build();

	        // 5. Build prompt
	        Prompt prompt = new Prompt(List.of(
	                systemMessage,
	                solutionMessage,
	                studentMessage
	        ));

	        // 6. Call model
	        ChatResponse response = chatModel.call(prompt);

	        String raw = response.getResult().getOutput().getText();

	        log.info("=== ALIGNMENT RESPONSE ===");
	        log.info(raw);

	        // 7. Parse aligned output
	        StudentPaperDto aligned = objectMapper.readValue(raw, StudentPaperDto.class);

	        return aligned;

	    } catch (Exception e) {
	        throw new RuntimeException("Failed to align student paper to solution structure", e);
	    }
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
	public SolutionDto transcribeSolutions(List<MultipartFile> solutionsImages) throws Exception {

		byte[] solutionPdfBytes = pdfAssemblyService.imagesToPdf(solutionsImages);
		Resource solutionPdf = new ByteArrayResource(solutionPdfBytes);

		return transcribeSolutions(solutionPdf);
	}

	public SolutionDto transcribeSolutions(Resource solutionPdf) throws Exception {

	SystemMessage systemMessage = new SystemMessage("""
			You are an exam solution transcription engine.

			TASK:
			Extract and normalize the official exam solutions from the attached PDF into the provided JSON schema.

			STRICT RULES:
			- Only extract information explicitly present in the solutions document.
			- Do NOT invent additional answers, explanations, or grading criteria.
			- Do NOT evaluate quality or assign marks.
			- Preserve original meaning and expected answer content as closely as possible.

			If information is unclear, missing, or unreadable:
			- set the field to null

			Ignore:
			- formatting issues
			- layout artifacts
			- OCR noise
			- repeated or misaligned text

			The goal is structural transcription, not reasoning.

			OUTPUT:
			Must strictly follow JSON schema.
			No extra fields.
			No commentary.

			JSON Schema:

			{
			  "subject": "string",
			
			  "questions": [
			    {
			      "questionId": "string",
			      "questionText": "string",
			      "modelAnswer": "string",
			      "maxMarks":int,
			      "keyPoints": ["string"],
			      "acceptableAlternativePoints": ["string"],
			      "evidenceReferences": ["string"]
			    }
			  ]
			}
			""");

	UserMessage solutionMessage = UserMessage.builder()
			.text("These are the official exam solutions.")
			.media(new Media(
					MimeTypeUtils.parseMimeType("application/pdf"),
					solutionPdf))
			.build();

	Prompt prompt = new Prompt(List.of(systemMessage, solutionMessage));

	ChatResponse response;
	try {
		response = chatModel.call(prompt);
	} catch (Exception e) {
		throw new RuntimeException("AI solution parsing failed", e);
	}

	var raw = response.getResult().getOutput().getText();

	log.info("====== Response from ai model for solution transcription: ========");
	log.info(raw);

	objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	SolutionDto dto = objectMapper.readValue(raw, SolutionDto.class);

	return dto;
}
	///
	/// 
	public StudentPaperDto transcribeStudentPaper(List<MultipartFile> studentpaperImages) throws Exception {

		byte[] studentpaperPdfBytes = pdfAssemblyService.imagesToPdf(studentpaperImages);
		Resource studentpaperPdf = new ByteArrayResource(studentpaperPdfBytes);

		return transcribeStudentPaper(studentpaperPdf);
	}

	public StudentPaperDto transcribeStudentPaper(Resource studentPaperPdf) throws Exception {

		SystemMessage systemMessage = new SystemMessage("""

				YOU ARE A STUDENT EXAM PAPER TRANSCRIPTION ENGINE.

				────────────────────────────────────────
				TASK
				────────────────────────────────────────

				Extract and structure a student's answer paper from a PDF into JSON.


				────────────────────────────────────────
				CORE STRUCTURE RULE
				────────────────────────────────────────

				- Printed text = question prompts / headers
				- Handwritten text below printed text = student answers
				- Preserve strict top-to-bottom order

				Each answer belongs to the nearest preceding printed question.

				────────────────────────────────────────
				SECTIONING RULE
				────────────────────────────────────────

				- rawQuestionLabel = main question number (e.g. "1.", "2.")
				- subQuestionLabel = subparts (e.g. "(a)", "(b)")

				- A rawQuestionLabel starts a new section
				- All following content belongs to that section until a new rawQuestionLabel appears

				- subQuestionLabel always belongs to the current rawQuestionLabel section

				────────────────────────────────────────
				SUBQUESTION RULE
				────────────────────────────────────────

				- subQuestionLabel NEVER creates a new section

				────────────────────────────────────────
				QUESTION TEXT RESOLUTION RULE (KEYED LOOKUP)
				────────────────────────────────────────
				
				- Use (rawQuestionLabel + subQuestionLabel) as a lookup key to determine questionText from printed sections (Questions).

  												
				────────────────────────────────────────
				GROUPING RULE
				────────────────────────────────────────

				- Maintain document order strictly
				- Handwritten content may span multiple blocks

								
				────────────────────────────────────────
				OUTPUT RULES
				────────────────────────────────────────

				Return ONLY valid JSON. No markdown. No explanation.

				────────────────────────────────────────
				OUTPUT SCHEMA
				────────────────────────────────────────

				{
				  "subject": "string",
				  "classAndSection": "string",
				  "date": "string",
				  "studentId": "string",
				  "questions": [
				    {
				      "questionId": null,
				      "rawQuestionLabel": "string",
				      "subQuestionLabel": "string",
				      "questionText": "string",
				      "answerText": "string"
				    }
				  ]
				}

				""");		
		
		
		
		UserMessage studentMessage = UserMessage.builder()
				.text("This is a student's answer paper. Handwritten parts are the answers and printed parts are questions.")
				.media(new Media(
						MimeTypeUtils.parseMimeType("application/pdf"),
						studentPaperPdf))
				.build();

		Prompt prompt = new Prompt(List.of(systemMessage, studentMessage));

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (Exception e) {
			throw new RuntimeException("AI student paper parsing failed", e);
		}

		var raw = response.getResult().getOutput().getText();

		log.info("====== Response from AI model for student paper transcription: ========");
		log.info(raw);

		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		StudentPaperDto dto = objectMapper.readValue(raw, StudentPaperDto.class);

		return dto;
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