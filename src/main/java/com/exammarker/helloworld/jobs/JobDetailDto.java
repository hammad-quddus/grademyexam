package com.exammarker.helloworld.jobs;

import java.time.LocalDateTime;
import java.util.List;

public class JobDetailDto {

    private Long id;
    private JobStatus status;
    private LocalDateTime createdAt;
    private List<PaperFileDto> papers;
	public Long getId() {
		return id;
	}
	public JobStatus getStatus() {
		return status;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public List<PaperFileDto> getPapers() {
		return papers;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setStatus(JobStatus status) {
		this.status = status;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public void setPapers(List<PaperFileDto> papers) {
		this.papers = papers;
	}

	




}