package com.exammarker.helloworld.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.dto.BandDto;
import com.exammarker.helloworld.dto.ConfidenceDto;
import com.exammarker.helloworld.dto.EvaluationDto;
import com.exammarker.helloworld.dto.ExamEvaluationDto;
import com.exammarker.helloworld.dto.QuestionEvaluationDto;
import com.exammarker.helloworld.dto.rubric.RubricDto;
import com.exammarker.helloworld.dto.rubric.RubricReferenceDto;
import com.exammarker.helloworld.dto.solution.TranscribedSolutionQuestionDto;
import com.exammarker.helloworld.dto.solution.TranscribedSolutionsDto;
import com.exammarker.helloworld.dto.studentpaper.TranscribedExamDto;
import com.exammarker.helloworld.dto.studentpaper.TranscribedQuestionDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class ExamEvaluationService {

	private static final Logger log = LoggerFactory.getLogger(ExamEvaluationService.class); // Fixed target logger class

	private final ChatModel chatModel; // Swapped to interface type

	private final PdfAssemblyService pdfAssemblyService;

	private final ObjectMapper objectMapper = new ObjectMapper();
	
	private final TaskExecutor taskExecutor;

	public ExamEvaluationService(ChatModel chatModel, PdfAssemblyService pdfAssemblyService, TaskExecutor taskExecutor) { // Fixed injection constructor
		this.chatModel = chatModel;
		this.pdfAssemblyService = pdfAssemblyService;
		this.taskExecutor = taskExecutor;
	}


	
	// legacy implementation
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
						        You are an experienced 9th-grade teacher.

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

						  "teacherComments": string,

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
	

	/**
	 * Phase 2: Evaluates a single pre-transcribed digital student answer against its matching official solution.
	 */
	public QuestionEvaluationDto evaluateSingleQuestion(
			TranscribedQuestionDto transcribedQuestion, 
			TranscribedSolutionQuestionDto matchedSolution,
			Resource rubricPdf) {

		SystemMessage systemMessage = new SystemMessage(
				"""
				You are an experienced academic evaluator.
				
				You will receive a clean digital transcription of a single question and student answer. 
				You are also supplied with the specific official solution guidelines for this exact question, along with the global grading rubrics.
				
				Tasks:
				1. Compare the transcribed student answer against the official solution's expectations.
				2. Evaluate across academic metrics: accuracy, coverage, use of source materials, structure, and relevance.
				3. Highlight missing critical keypoints (coverage gaps) as defined in the official solution.
				4. Assign marks fairly but strictly according to the rubric's mark allocations and the question's max marks.
				5. Provide supportive teacher feedback and document any factual errors.
				6. If transcription contains "[unreadable handwriting]", flag for human review.
				
				Rules:
				- Grade strictly on evidence found in the transcribed text. Do not hallucinate or invent answers.
				- Return ONLY valid JSON matching the schema below.
				
				JSON schema:
				{
				  "questionId": "string",
				  "questionText": "string",
				  "maxMarks": integer,
				  "marksAwarded": integer,
				  "studentAnswerTranscription": "string",
				  "officialSolutionKeyPoints": [ "string" ],
				  "coverageGaps": [ "string" ],
				  "evaluation": {
				    "accuracy": [ "string" ],
				    "coverage": [ "string" ],
				    "useOfResources": [ "string" ],
				    "structure": [ "string" ],
				    "relevance": [ "string" ]
				  },
				  "evaluationSummary": "string",
				  "strengths": [ "string" ],
				  "improvements": [ "string" ],
				  "factualErrors": [ "string" ],
				  "teacherComments": "string",
				  "rubricReference": {
				    "band": {
				      "min": integer,
				      "max": integer
				    },
				    "descriptor": "string"
				  },
				  "confidence": {
				    "transcriptionConfidence": number,
				    "gradingConfidence": number
				  },
				  "requiresHumanReview": boolean
				}
				""");

		String studentAnswerChunk = String.format(
				"--- STUDENT SUBMISSION ---\nQuestion ID: %s\nQuestion Text: %s\nTranscribed Answer: %s\n",
				transcribedQuestion.questionId(),
				transcribedQuestion.questionText(),
				transcribedQuestion.answerText()
		);

		String solutionsText = matchedSolution != null 
				? String.format(
						"--- OFFICIAL EXAM SOLUTION ---\nQuestion ID: %s\nMax Marks: %d\nExpected Key Points:\n- %s\nMarking Guidelines: %s\n",
						matchedSolution.questionId(),
						matchedSolution.maxMarks(),
						String.join("\n- ", matchedSolution.officialSolutionKeyPoints()),
						matchedSolution.markingGuidelines()
				  )
				: "--- OFFICIAL EXAM SOLUTION ---\nNo matching official solution segment was successfully transcribed for this question ID.";

		UserMessage studentMessage = UserMessage.builder().text(studentAnswerChunk).build();
		UserMessage solutionsMessage = UserMessage.builder().text(solutionsText).build();

		UserMessage rubricMessage = UserMessage.builder()
				.text("This is the global marking criteria / grade boundary rubrics.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), rubricPdf))
				.build();

		Prompt prompt = new Prompt(List.of(systemMessage, rubricMessage, solutionsMessage, studentMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		BeanOutputConverter<QuestionEvaluationDto> converter = new BeanOutputConverter<>(QuestionEvaluationDto.class);
		return converter.convert(rawJson);
	}
	
	
	/**
	 * Orchestrates the full grading pipeline for multi-page papers.
	 * @param paperImages    Handwritten student paper images.
	 * @param rubricImages   Images of the marking criteria / rubric.
	 * @param solutionImages Images of the model answers / answer keys (possibly out of order).
	 * @return Consolidated exam evaluation report.
	 */
	public ExamEvaluationDto evaluateEntireExamPipeline(
			List<MultipartFile> paperImages, 
			List<MultipartFile> rubricImages,
			List<MultipartFile> solutionImages) throws Exception {

		log.info("Starting Phase 1a: Transcribing and Segmenting Student Paper ({} pages)...", paperImages.size());
//		TranscribedExamDto transcription = transcribeAndSegmentPaper(paperImages);

		CompletableFuture<TranscribedExamDto> transcribeAndSegmentPaperFuture =
			    CompletableFuture.supplyAsync(() -> {
			        try {
						return transcribeAndSegmentPaper(paperImages);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						throw new RuntimeException("Failed to transcribe paper", e);
					}
			    }, taskExecutor);
		
		log.info("Starting Phase 1b: Transcribing and Structuring Official Solutions ({} pages)...", solutionImages.size());
//		TranscribedSolutionsDto officialSolutions = transcribeOfficialSolutions(solutionImages);

		CompletableFuture<TranscribedSolutionsDto> transcribeOfficialSolutionsFuture =
			    CompletableFuture.supplyAsync(() -> {
			        try {
						return transcribeOfficialSolutions(solutionImages);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						throw new RuntimeException("Failed to transcribe solutions", e);
					}
			    }, taskExecutor);

		TranscribedExamDto transcription = transcribeAndSegmentPaperFuture.join();
		TranscribedSolutionsDto officialSolutions = transcribeOfficialSolutionsFuture.join();
		
		log.info("Transcription completed.\n" +
				"Student: {}, ID: {}, Subject: {}, Class: {}, Date: {}\n" +
				"Student Questions Found: {}\n" +
				"Official Solution Keys Mapped: {}", 
				transcription.studentName(), 
				transcription.studentId(),
				transcription.subject(), 
				transcription.classAndSection(),
				transcription.date(),
				transcription.questions().size(),
				officialSolutions.questions().size());

		// Compile rubric into digital resources (rubrics are short and global, so we can keep as PDF)
		byte[] rubricPdfBytes = pdfAssemblyService.imagesToPdf(rubricImages);
		Resource rubricPdf = new ByteArrayResource(rubricPdfBytes);

		List<QuestionEvaluationDto> evaluatedQuestions = new ArrayList<>();
		int totalMaxMarks = 0;
		int totalMarksAwarded = 0;

		log.info("Starting Phase 2: Iterative evaluation loop matching student answers to structured solutions...");
		for (TranscribedQuestionDto transcribedQuestion : transcription.questions()) {
			try {
				log.info("Evaluating Question ID: {}", transcribedQuestion.questionId());
				
				// Dynamically resolve and match the correct structured official solution
				TranscribedSolutionQuestionDto matchedSolution = officialSolutions.questions().stream()
						.filter(sol -> sol.questionId().equalsIgnoreCase(transcribedQuestion.questionId()))
						.findFirst()
						.orElse(null);
				
				// if no match by questionId try questionText
				if (matchedSolution == null) {
					matchedSolution = officialSolutions.questions().stream()
							.filter(sol -> sol.questionText().equalsIgnoreCase(transcribedQuestion.questionText()))
							.findFirst()
							.orElse(null);				}
				
				
				if (matchedSolution == null) {
					log.warn("No official solution found matching questionId: {}. Grading will proceed with caution.", 
							transcribedQuestion.questionId());
				}

				QuestionEvaluationDto evalDto = evaluateSingleQuestion(
						transcribedQuestion, 
						matchedSolution,
						rubricPdf
				);
				evaluatedQuestions.add(evalDto);
				
				if (evalDto.maxMarks() != null) {
					totalMaxMarks += evalDto.maxMarks();
				}
				if (evalDto.marksAwarded() != null) {
					totalMarksAwarded += evalDto.marksAwarded();
				}
			} catch (Exception e) {
				log.error("Failed to evaluate question ID: {}", transcribedQuestion.questionId(), e);
				// Fallback dynamically so one difficult question doesn't break the entire pipeline execution
				QuestionEvaluationDto fallback = createFallbackEvaluation(transcription.studentName(), transcribedQuestion, e.getMessage());
				evaluatedQuestions.add(fallback);
				if (fallback.maxMarks() != null) {
					totalMaxMarks += fallback.maxMarks();
				}
				if (fallback.marksAwarded() != null) {
					totalMarksAwarded += fallback.marksAwarded();
				}
			}
		}

		// Assemble the final consolidated response using modern constructor matching
		ExamEvaluationDto finalReport = new ExamEvaluationDto(
				transcription.studentName(),
				transcription.studentId(),
				transcription.subject(),
				transcription.classAndSection(),
				transcription.date(),
				totalMaxMarks,
				totalMarksAwarded,
				evaluatedQuestions
		);

		log.info("Pipeline completed successfully for student: {} (Score: {}/{})", 
				finalReport.studentName(), totalMarksAwarded, totalMaxMarks);
		return finalReport;
	}
	

	
	
	/**
	 * Creates a structured fallback object in case evaluation of a single question fails.
	 */
	private QuestionEvaluationDto createFallbackEvaluation(String studentName, TranscribedQuestionDto transcribedQuestion, String errorMsg) {
		return new QuestionEvaluationDto(
				studentName,
				transcribedQuestion.questionId(),
				transcribedQuestion.questionText(),
				10, // Default fallback max score
				0,  // Standard default scored marks
				transcribedQuestion.answerText(),
				List.of(),
				new EvaluationDto(List.of(), List.of(), List.of(), List.of(), List.of()),
				"Evaluation failed due to pipeline error.",
				List.of(), // strengths
				List.of(), // improvements
				List.of(), // factualErrors
				List.of(), // coverageGaps
				"System error occurred during automated grading: " + errorMsg, // teacherComments
				new RubricReferenceDto(new BandDto(0, 0), "Fallback"),
				new ConfidenceDto(0.0, 0.0),
				true
		);
	}	
}