package com.exammarker.helloworld.evalutation.dto.solution;

import java.util.List;

public record TranscribedSolutionsDto(
		String subject,
		String examCode,
		List<TranscribedSolutionQuestionDto> questions
) {}