package com.exammarker.helloworld.evalutation.dto.rubric;

import java.util.List;

public record RubricCategoryDto(
        String rubricCategoryId,
        String assessmentObjective,
        String description,
        String scoringRule,
        List<RubricLevelDto> levels
) {}