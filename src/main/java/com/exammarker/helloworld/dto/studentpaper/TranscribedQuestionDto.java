package com.exammarker.helloworld.dto.studentpaper;
public record TranscribedQuestionDto(
		String questionId,
		String questionText,
		String answerText,
		int maxMarks
) {}