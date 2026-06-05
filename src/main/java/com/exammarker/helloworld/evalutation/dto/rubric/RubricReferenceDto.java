package com.exammarker.helloworld.evalutation.dto.rubric;

import com.exammarker.helloworld.evaluation.dto.BandDto;

// judgement layer
public record RubricReferenceDto(

    BandDto band,

    String descriptor

) {}