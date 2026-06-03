package com.exammarker.helloworld.evalutation.dto.rubric;
import java.util.List;

public record RubricDto(
        String rubricId,
        String subject,
        List<RubricCategoryDto> rubricCategories,
        List<QuestionMappingDto> questionMappings
) {}