package com.exammarker.helloworld.jobs;

import java.time.LocalDateTime;

public class JobDto {

    private Long id;

    private LocalDateTime createdAt;

    private JobStatus status;

    private int paperCount;


    public JobDto(Job job) {
        this.id = job.getId();
        this.createdAt = job.getCreatedAt();
        this.status = job.getStatus();

        this.paperCount = job.getPapers().size();
    }


    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getPaperCount() {
        return paperCount;
    }
}