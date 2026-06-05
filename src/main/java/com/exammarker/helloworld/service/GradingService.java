package com.exammarker.helloworld.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
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

import com.exammarker.helloworld.evaluation.dto.BandDto;
import com.exammarker.helloworld.evaluation.dto.ConfidenceDto;
import com.exammarker.helloworld.evaluation.dto.EvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.QuestionEvaluationDto;
import com.exammarker.helloworld.evalutation.dto.rubric.RubricReferenceDto;
import com.exammarker.helloworld.evalutation.dto.solution.TranscribedSolutionQuestionDto;
import com.exammarker.helloworld.evalutation.dto.solution.TranscribedSolutionsDto;
import com.exammarker.helloworld.evalutation.dto.studentpaper.TranscribedExamDto;
import com.exammarker.helloworld.evalutation.dto.studentpaper.TranscribedQuestionDto;
import com.exammarker.helloworld.solution.SolutionExtractionEntity;
import com.exammarker.helloworld.solution.SolutionService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GradingService {

	private static final Logger log = LoggerFactory.getLogger(GradingService.class);

	private final ChatModel chatModel;

	private final PdfAssemblyService pdfAssemblyService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final TaskExecutor taskExecutor;
	
	private final SolutionService solutionService;

	public GradingService(ChatModel chatModel, PdfAssemblyService pdfAssemblyService,
			TaskExecutor taskExecutor, SolutionService solutionService) {
		this.chatModel = chatModel;
		this.pdfAssemblyService = pdfAssemblyService;
		this.taskExecutor = taskExecutor;
		this.solutionService = solutionService;
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}


	/**
	 * Phase 1b: Transcribes and structures out-of-order official exam solutions and marking criteria.
	 */
	public TranscribedSolutionsDto transcribeOfficialSolutions(List<MultipartFile> solutionImages) throws Exception {
		
		Optional<SolutionExtractionEntity> solutionExtractionEntity = solutionService.findByFiles(solutionImages);
		if(solutionExtractionEntity.isPresent()) {
			log.info("Entry found in cache for files: {}", solutionExtractionEntity.get().getDocumentHash());
			
			String rawJson = solutionExtractionEntity.get().getExtractionJson();
			BeanOutputConverter<TranscribedSolutionsDto> converter = new BeanOutputConverter<>(TranscribedSolutionsDto.class);
			return converter.convert(rawJson);		
		}
		
		log.info("Cache miss for solutions document. Initiating AI OCR transcription...");
		
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
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), solutionsPdf)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, solutionsMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		BeanOutputConverter<TranscribedSolutionsDto> converter = new BeanOutputConverter<>(TranscribedSolutionsDto.class);
		TranscribedSolutionsDto res = converter.convert(rawJson);
		
		solutionService.save(solutionImages, rawJson);
		
		
		return res;
	}

	/**
	 * Phase 1a: Transcribes and segments handwritten student exam pages into structured question-answer pairs.
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
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), studentWorkPdf)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, paperMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		log.info("====== Response from ai model for studentpaper transcription: ========");
		log.info(rawJson);

		BeanOutputConverter<TranscribedExamDto> converter = new BeanOutputConverter<>(TranscribedExamDto.class);
		return converter.convert(rawJson);
	}

	/**
	 * Phase 2: Evaluates a single pre-transcribed digital student answer against its matching official solution.
	 * Strictly adheres to its 3-parameter signature.
	 */
	public QuestionEvaluationDto evaluateSingleQuestion(TranscribedQuestionDto transcribedQuestion,
			TranscribedSolutionQuestionDto matchedSolution, Resource rubricPdf) {

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
				  "studentName": "string or null",
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
				transcribedQuestion.questionId(), transcribedQuestion.questionText(), transcribedQuestion.answerText());

		String solutionsText = matchedSolution != null ? String.format(
				"--- OFFICIAL EXAM SOLUTION ---\nQuestion ID: %s\nMax Marks: %d\nExpected Key Points:\n- %s\nMarking Guidelines: %s\n",
				matchedSolution.questionId(), matchedSolution.maxMarks(),
				String.join("\n- ", matchedSolution.officialSolutionKeyPoints()), matchedSolution.markingGuidelines())
				: "--- OFFICIAL EXAM SOLUTION ---\nNo matching official solution segment was successfully transcribed for this question ID.";

		UserMessage studentMessage = UserMessage.builder().text(studentAnswerChunk).build();
		UserMessage solutionsMessage = UserMessage.builder().text(solutionsText).build();

		UserMessage rubricMessage = UserMessage.builder()
				.text("This is the global marking criteria / grade boundary rubrics.")
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), rubricPdf)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, rubricMessage, solutionsMessage, studentMessage));
		ChatResponse response = chatModel.call(prompt);
		String rawJson = response.getResult().getOutput().getText();

		BeanOutputConverter<QuestionEvaluationDto> converter = new BeanOutputConverter<>(QuestionEvaluationDto.class);
		return converter.convert(rawJson);
	}

	/**
	 * Intermediate Alignment Step:
	 * Takes the two raw transcription models, passes them to a fast, text-only AI model call, 
	 * and returns a clean TranscribedExamDto where the student's transcribed responses 
	 * are strictly re-organized, aligned, and matched directly to the verified official solution slots.
	 */
	public TranscribedExamDto alignTranscriptionsWithAI(TranscribedExamDto studentPaper, 
			TranscribedSolutionsDto officialSolutions) throws Exception {

		log.info("Starting Intermediate AI-Driven Alignment Pass (Text-to-Text)...");

		SystemMessage systemMessage = new SystemMessage(
				"""
				You are an advanced academic mapping assistant.

				TASK:
				Compare the list of transcribed student questions against the list of official solution templates.
				Resolve any formatting mismatches, naming chaos, or shorthand ID discrepancies. Re-organize, align, 
				and map each student question answer transcription block strictly to its correct official solution questionId.

				RULES:
				1. Re-organize and match the student's answers so they map strictly to the official "questionId"s.
				2. If the student wrote their answers to multiple sub-questions in a single continuous transcription block (e.g. Q1), 
				   split that single transcribed block logically and map it to both separate subpart official solutions (e.g., Q1a and Q1b).
				3. If the student has completely skipped an official question, set "answerText" to "Skipped" in the questions list.
				4. **Sub-question Mark Distribution**: If a parent question contains a total mark value (e.g., 4 marks) 
				   and is split into multiple distinct subparts (e.g., subparts `(i)` and `(ii)`, or `a` and `b`) but the 
				   solutions do not explicitly allocate separate marks for each subpart, divide the parent question's total 
				   marks equally among those subparts in the final mapping. For example, if a parent question has a total 
				   of 4 marks and is split into two mapped sub-questions, assign `maxMarks = 2` to each subpart in the mapped output.
				5. Output must preserve student header metadata such as studentName, classAndSection, subject, etc.
				6. Return ONLY valid JSON matching the TranscribedExamDto schema below. Do not wrap in markdown or backticks.

				JSON schema:
				{
				  "subject": "string or null",
				  "classAndSection": "string or null",
				  "date": "string or null",
				  "studentId": "string or null",
				  "studentName": "string or null",
				  "questions": [
				    {
				      "questionId": "string (MUST match official questionId exactly)",
				      "questionText": "string (MUST match official questionText exactly)",
				      "answerText": "string (the student's transcribed answer)",
				      "maxMarks": integer
				    }
				  ]
				}
				""");

		// Serialize transcription datasets to raw JSON strings
		String studentPaperJson = objectMapper.writeValueAsString(studentPaper);
		String officialSolutionsJson = objectMapper.writeValueAsString(officialSolutions);

		String promptInput = String.format(
				"--- STUDENT PAPER TRANSCRIPTION ---\n%s\n\n--- OFFICIAL EXAM SOLUTIONS ---\n%s\n",
				studentPaperJson,
				officialSolutionsJson
		);

		UserMessage userMessage = UserMessage.builder().text(promptInput).build();
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (Exception e) {
			throw new RuntimeException("AI alignment phase failed", e);
		}

		String rawJson = response.getResult().getOutput().getText();
		log.info("====== Response from ai model for alignment step: ========");
		log.info(rawJson);

		BeanOutputConverter<TranscribedExamDto> converter = new BeanOutputConverter<>(TranscribedExamDto.class);
		return converter.convert(rawJson);
	}

	/**
	 * Orchestrates the full grading pipeline for multi-page papers concurrently.
	 * Backed by a concurrent Fork-Join-Fork alignment model using TaskExecutor.
	 */
	public ExamEvaluationDto evaluateEntireExamPipeline(
			List<MultipartFile> paperImages,
			List<MultipartFile> rubricImages, 
			List<MultipartFile> solutionImages) throws Exception {

		log.info("Starting Concurrency Phase 1: Forking Student Paper & Solution Transcriptions...");

		// FORK Phase 1: Run both transcription tasks concurrently on separate threads
		CompletableFuture<TranscribedExamDto> transcribeAndSegmentPaperFuture = CompletableFuture.supplyAsync(() -> {
			try {
				return transcribeAndSegmentPaper(paperImages);
			} catch (Exception e) {
				throw new RuntimeException("Failed to transcribe student paper", e);
			}
		}, taskExecutor);

		CompletableFuture<TranscribedSolutionsDto> transcribeOfficialSolutionsFuture = CompletableFuture.supplyAsync(() -> {
			try {
				return transcribeOfficialSolutions(solutionImages);
			} catch (Exception e) {
				throw new RuntimeException("Failed to transcribe official solutions", e);
			}
		}, taskExecutor);

		// BARRIER Phase 1: Block synchronously until both parallel transcription operations finish
		CompletableFuture.allOf(transcribeAndSegmentPaperFuture, transcribeOfficialSolutionsFuture).join();

		TranscribedExamDto transcription = transcribeAndSegmentPaperFuture.join();
		TranscribedSolutionsDto officialSolutions = transcribeOfficialSolutionsFuture.join();

		log.info(
				"Transcription completed.\n" + "Student: {}, ID: {}, Subject: {}, Class: {}, Date: {}\n"
						+ "Student Questions Found: {}\n" + "Official Solution Keys Mapped: {}",
				transcription.studentName(), transcription.studentId(), transcription.subject(),
				transcription.classAndSection(), transcription.date(), transcription.questions().size(),
				officialSolutions.questions().size());

		// AI ALIGNMENT PHASE: Fast text-only pass utilizing TranscribedExamDto directly to align structures and split blocks
		TranscribedExamDto alignedTranscription = alignTranscriptionsWithAI(transcription, officialSolutions);

		// Compile rubric into digital resources (rubrics are short and global, so we can keep as PDF)
		byte[] rubricPdfBytes = pdfAssemblyService.imagesToPdf(rubricImages);
		Resource rubricPdf = new ByteArrayResource(rubricPdfBytes);

		log.info("Starting Concurrency Phase 2: Forking Evaluations for all {} matched questions...", alignedTranscription.questions().size());

		// FORK Phase 2: Launch parallel grading tasks for each matched aligned question block
		List<CompletableFuture<QuestionEvaluationDto>> evaluatedQuestionsFutures = new ArrayList<>();

		for (TranscribedQuestionDto alignedQuestion : alignedTranscription.questions()) {

			log.info("Scheduling Evaluation for Official Question ID: {}", alignedQuestion.questionId());

			// In-memory lookup: Match strictly by the cleaned, AI-aligned questionId
			final TranscribedSolutionQuestionDto matchedSolution = officialSolutions.questions().stream()
					.filter(sol -> sol.questionId().equalsIgnoreCase(alignedQuestion.questionId()))
					.findFirst()
					.orElse(null);

			if (matchedSolution == null) {
				log.warn("No official solution found matching questionId: {}. Grading will proceed with caution.",
						alignedQuestion.questionId());
			}

			// Schedule individual question grading on parallel thread pools with high fault-tolerance
			CompletableFuture<QuestionEvaluationDto> evaluateSingleQuestionFuture = CompletableFuture.supplyAsync(() -> {
				try {
					log.info("Async Thread [Evaluation - {}] started.", alignedQuestion.questionId());
					return evaluateSingleQuestion(
							alignedQuestion, 
							matchedSolution, 
							rubricPdf
					);
				} catch (Exception e) {
					log.error("Failed to evaluate question ID: {}", alignedQuestion.questionId(), e);
					// Fallback dynamically so one failing question thread doesn't crash the entire transaction execution
					return createFallbackEvaluation(alignedTranscription.studentName(), alignedQuestion, e.getMessage());
				}
			}, taskExecutor);

			evaluatedQuestionsFutures.add(evaluateSingleQuestionFuture);
		}

		// BARRIER Phase 2: Wait until all parallel question evaluation threads complete their executions
		CompletableFuture.allOf(evaluatedQuestionsFutures.toArray(new CompletableFuture[0])).join();

		// JOIN Phase: Safely map and collect completed futures
		List<QuestionEvaluationDto> evaluatedQuestions = evaluatedQuestionsFutures.stream()
				.map(CompletableFuture::join)
				.toList();

		// AGGREGATION Phase: Dynamically calculate final scored marks and paper weightings
		int totalMaxMarks = 0;
		int totalMarksAwarded = 0;

		for (QuestionEvaluationDto evalDto : evaluatedQuestions) {
			if (evalDto.maxMarks() != null) {
				totalMaxMarks += evalDto.maxMarks();
			}
			if (evalDto.marksAwarded() != null) {
				totalMarksAwarded += evalDto.marksAwarded();
			}
		}

		// Assemble the final consolidated response using modern constructor matching
		ExamEvaluationDto finalReport = new ExamEvaluationDto(
				alignedTranscription.studentName(), 
				alignedTranscription.studentId(),
				alignedTranscription.subject(), 
				alignedTranscription.classAndSection(), 
				alignedTranscription.date(), 
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
				0,  // Default scored marks
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