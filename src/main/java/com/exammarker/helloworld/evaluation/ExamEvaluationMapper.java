package com.exammarker.helloworld.evaluation;

import java.time.Instant;
import java.util.List;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.evaluation.dto.QuestionEvaluationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExamEvaluationMapper {

	private static final ObjectMapper mapper = new ObjectMapper();

	public static ExamEvaluationDto toDto(ExamEvaluationEntity e) {

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

	public static ExamEvaluationEntity toEntity(ExamEvaluationDto dto) {

		String questionsJson;
		try {
			questionsJson = mapper.writeValueAsString(dto.evaluatedQuestions());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		ExamEvaluationEntity entity = new ExamEvaluationEntity(dto.studentName(), dto.studentId(), dto.subject(),
				dto.classAndSection(), dto.date(), dto.totalMaxMarks(), dto.totalMarksAwarded(), questionsJson,
				Instant.now());

		return entity;
	}
	
	public static ExamEvaluationSummaryDto toSummaryDto(ExamEvaluationEntity e) {

	    return new ExamEvaluationSummaryDto(
	        e.getId(),
	        e.getStudentName(),
	        e.getCreatedAt()
	    );
	}
	
	
}