package com.exammarker.helloworld.evalutation.dto.rubric;
public record QuestionMappingDto(
        String questionId,
        String rubricCategoryId,
        Integer maxMarks
) {}