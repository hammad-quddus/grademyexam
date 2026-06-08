package com.exammarker.helloworld.jobs;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL)
    private List<PaperFile> papers = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = JobStatus.ACCEPTED;
        }
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

    public void setStatus(JobStatus status) {
        this.status = status;
    }

	public List<PaperFile> getPapers() {
		return papers;
	}
    
	public void addPaper(PaperFile paper) {
	    papers.add(paper);
	}
    
}