package com.exammarker.helloworld.dto.studentpaper;
record TranscribedQuestionDto(
		String questionId,
		String questionText,
		String answerText,
		int maxMarks
) {}