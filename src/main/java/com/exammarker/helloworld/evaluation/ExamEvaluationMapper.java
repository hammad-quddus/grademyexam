package com.exammarker.helloworld.evaluation;

import java.time.Instant;
import java.util.List;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.evaluation.dto.QuestionEvaluationDto;
import com.exammarker.helloworld.jobs.Job;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExamEvaluationMapper {

	private static final ObjectMapper mapper = new ObjectMapper();

	public static ExamEvaluationDto toDto(ExamEvaluation e) {

		List<QuestionEvaluationDto> questions = parseQuestions(e.getEvaluatedQuestionsJson());

		return new ExamEvaluationDto(e.getStudentName(), e.getStudentId(), e.getSubject(), e.getClassAndSection(),
				e.getDate(), e.getTotalMaxMarks(), e.getTotalMarksAwarded(), questions);
	}

	private static List<QuestionEvaluationDto> parseQuestions(String json) {
		try {
			return mapper.readValue(json, new TypeReference<List<QuestionEvaluationDto>>() {
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static ExamEvaluation toEntity(
	        ExamEvaluationDto dto,
	        Job job
	) {

	    String questionsJson;
	    try {
	        questionsJson = mapper.writeValueAsString(dto.evaluatedQuestions());
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    }

	    return new ExamEvaluation(
	            job,
	            dto.studentName(),
	            dto.studentId(),
	            dto.subject(),
	            dto.classAndSection(),
	            dto.date(),
	            dto.totalMaxMarks(),
	            dto.totalMarksAwarded(),
	            questionsJson,
	            Instant.now()
	    );
	}
	
	public static ExamEvaluationSummaryDto toSummaryDto(ExamEvaluation e) {

	    return new ExamEvaluationSummaryDto(
	        e.getId(),
	        e.getStudentName(),
	        e.getCreatedAt()
	    );
	}
	
	
}