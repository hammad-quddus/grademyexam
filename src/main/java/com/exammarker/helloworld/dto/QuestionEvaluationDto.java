package com.exammarker.helloworld.dto;

import java.util.List;

import com.exammarker.helloworld.dto.rubric.RubricReferenceDto;


// raw output layer
public record QuestionEvaluationDto(

	String studentName,	
	String questionId,
    String questionText,

    Integer maxMarks,
    Integer marksAwarded,

    String studentAnswerTranscription,

    List<String> officialSolutionKeyPoints,

    EvaluationDto evaluation,
    String evaluationSummary,

    List<String> strengths,
    List<String> improvements,
    List<String> factualErrors,
    List<String> coverageGaps,

    String teacherComments,
    
    RubricReferenceDto rubricReference,

    ConfidenceDto confidence,

    Boolean requiresHumanReview

//    List<EvidenceDto> evidence

) {}