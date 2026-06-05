package com.exammarker.helloworld.evalutation.dto.rubric;
import java.util.List;

import com.exammarker.helloworld.evaluation.dto.MarkRangeDto;

public record LevelScaleDto(
        Integer levelNumber,
        String label,
        MarkRangeDto markRange,
        String descriptor,
        List<String> characteristics,
        List<String> evidenceKeywords
) {}