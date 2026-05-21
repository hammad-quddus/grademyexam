package com.exammarker.helloworld.dto.studentpaper;

import java.util.List;

public record TranscribedExamDto(
		String subject,
		String classAndSection,
		String date,
		String studentId,
		String studentName,
		List<TranscribedQuestionDto> questions
) {}