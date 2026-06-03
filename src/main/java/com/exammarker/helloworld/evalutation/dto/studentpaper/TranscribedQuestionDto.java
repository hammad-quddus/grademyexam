package com.exammarker.helloworld.evalutation.dto.studentpaper;
public record TranscribedQuestionDto(
		String questionId,
		String questionText,
		String answerText,
		int maxMarks
) {}