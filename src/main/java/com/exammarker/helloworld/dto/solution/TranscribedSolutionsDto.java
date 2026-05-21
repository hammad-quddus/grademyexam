package com.exammarker.helloworld.dto.solution;

import java.util.List;

public record TranscribedSolutionsDto(
		String subject,
		String examCode,
		List<TranscribedSolutionQuestionDto> questions
) {}