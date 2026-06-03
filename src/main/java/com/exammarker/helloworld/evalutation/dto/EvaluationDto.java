package com.exammarker.helloworld.evalutation.dto;

import java.util.List;

// analytical layer
public record EvaluationDto(

    List<String> accuracy,
    List<String> coverage,
    List<String> useOfResources,
    List<String> structure,
    List<String> relevance

) {}