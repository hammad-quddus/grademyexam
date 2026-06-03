package com.exammarker.helloworld.evalutation.dto;

// meta layer
public record ConfidenceDto(
    Double transcriptionConfidence,
    Double gradingConfidence
) {}