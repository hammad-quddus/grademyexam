package com.exammarker.helloworld.evaluation.dto;

import java.util.List;

public record ExamEvaluationDto(
		String studentName,
		String studentId,
		String subject,
		String classAndSection,
		String date,
		int totalMaxMarks,
		int totalMarksAwarded,
		List<QuestionEvaluationDto> evaluatedQuestions
) {}