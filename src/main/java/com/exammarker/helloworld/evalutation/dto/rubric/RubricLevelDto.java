package com.exammarker.helloworld.evalutation.dto.rubric;

import java.util.List;

public record RubricLevelDto(
        String levelId,
        Integer levelNumber,
        MarkRangeDto markRange,
        String descriptor,
        List<String> characteristics,
        List<String> evidenceKeywords
) {}