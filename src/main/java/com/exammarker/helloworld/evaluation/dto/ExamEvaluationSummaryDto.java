package com.exammarker.helloworld.evaluation.dto;

import java.time.Instant;


public class ExamEvaluationSummaryDto {

	private Long id;

    private String studentName;

    private Instant createdAt;

    public ExamEvaluationSummaryDto(Long id, String studentName, Instant createdAt) {
    	this.id = id;
    	this.studentName = studentName;
    	this.createdAt = createdAt;
	}

    public Long getId() { return id; }
    public String getStudentName() { return studentName; }
    public Instant getCreatedAt() { return createdAt; }
}