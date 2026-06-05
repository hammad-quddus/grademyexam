package com.exammarker.helloworld.evaluation.dto;

// meta layer
public record ConfidenceDto(
    Double transcriptionConfidence,
    Double gradingConfidence
) {}