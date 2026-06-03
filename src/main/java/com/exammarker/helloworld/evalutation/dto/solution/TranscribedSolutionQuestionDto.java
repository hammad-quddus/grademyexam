package com.exammarker.helloworld.evalutation.dto.solution;

import java.util.List;

public record TranscribedSolutionQuestionDto(
		String questionId,
		String questionText,
		int maxMarks,
		List<String> officialSolutionKeyPoints,
		String markingGuidelines
) {}