package com.exammarker.helloworld.service;

import java.util.List;

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
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.dto.QuestionEvaluationDto;
import com.exammarker.helloworld.evalutation.dto.rubric.RubricDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LegacyGradingService {
	private static final Logger log = LoggerFactory.getLogger(GradingService.class);

	private final ChatModel chatModel;

	private final PdfAssemblyService pdfAssemblyService;

	private final ObjectMapper objectMapper = new ObjectMapper();



	public LegacyGradingService(ChatModel chatModel, PdfAssemblyService pdfAssemblyService) {
		this.chatModel = chatModel;
		this.pdfAssemblyService = pdfAssemblyService;
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	/**
	 * Legacy implementation to grade a single question from raw file attachments.
	 */
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
				  "officialSolutionKeyPoints": [ string ],
				  "coverageGaps": [ string ],
				  "evaluation": {
				    "accuracy": [ string ],
				    "coverage": [ string ],
				    "useOfResources": [ string ],
				    "structure": [ string ],
				    "relevance": [ string ]
				  },
				  "evaluationSummary": string,
				  "strengths": [ string ],
				  "improvements": [ string ],
				  "factualErrors": [ string ],
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

		BeanOutputConverter<QuestionEvaluationDto> converter = new BeanOutputConverter<>(QuestionEvaluationDto.class);
		QuestionEvaluationDto dto = converter.convert(raw);

		return dto;
	}

//	public RubricDto transcribeRubric(List<MultipartFile> rubricImages) throws Exception {
//		byte[] rubricPdfBytes = rubricImages.get(0).getBytes();
//		Resource rubricPdf = new ByteArrayResource(rubricPdfBytes);
//		return transcribeRubric(rubricPdf);
//	}

	public RubricDto transcribeRubric(List<MultipartFile> rubricImages) throws Exception {
		byte[] rubricPdfBytes = rubricImages.get(0).getBytes();
		Resource rubricResource = new ByteArrayResource(rubricPdfBytes);
		
		
		SystemMessage systemMessage = new SystemMessage("""
				You are an AI specialized in transforming academic assessment rubrics into structured JSON.

			    Task:
			    Analyze the attached Rubric PDF. Extract the grading levels, assessment objectives (AO), mark schemes, 
			    and descriptors, and map them to the provided JSON schema.
			
			    Normalization Rules:
			    1. Contextual Marks: Since Question 1(a) and Questions 2-5 have different mark allocations for the same level, 
			       create a granular representation in the JSON. If a level applies to both, assign the specific mark range 
			       to each question type context within the level object.
			    2. Best-Fit Logic: Ensure the 'descriptor' and 'characteristics' are verbatim or summarized clearly for 
			       'best-fit' evaluation.
			    3. Structural Cleanliness: 
			       - Do NOT output markdown code blocks.
			       - Do NOT output preamble or conversational text.
			       - Return ONLY raw JSON.

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
				          "characteristics": [ "string" ],
				          "evidenceKeywords": [ "string" ]
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
				.media(new Media(MimeTypeUtils.parseMimeType("application/pdf"), rubricResource)).build();

		Prompt prompt = new Prompt(List.of(systemMessage, rubricMessage));

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (Exception e) {
			throw new RuntimeException("AI parsing failed", e);
		}

		var raw = response.getResult().getOutput().getText();
		raw = extractJsonBlock(raw);
		log.info("====== Response from ai model for rubric transcription: ========");
		log.info(raw);

		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return objectMapper.readValue(raw, RubricDto.class);
	}
	
	private String extractJsonBlock(String text) {
	    int start = text.indexOf("{");
	    int end = text.lastIndexOf("}");

	    if (start == -1 || end == -1 || end <= start) {
	        throw new RuntimeException("No valid JSON found in LLM output:\n" + text);
	    }

	    return text.substring(start, end + 1);
	}

}
