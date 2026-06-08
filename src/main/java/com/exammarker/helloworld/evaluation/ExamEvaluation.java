package com.exammarker.helloworld.evaluation;

import java.time.Instant;

import com.exammarker.helloworld.jobs.Job;

import jakarta.persistence.*;

@Entity
@Table(name = "exam_evaluation")
public class ExamEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;


    private String studentName;
    private String studentId;
    private String subject;
    private String classAndSection;
    private String date;

    private int totalMaxMarks;
    private int totalMarksAwarded;

    @Lob
    private String evaluatedQuestionsJson;

    private Instant createdAt;


    protected ExamEvaluation() {}


    public ExamEvaluation(
            Job job,
            String studentName,
            String studentId,
            String subject,
            String classAndSection,
            String date,
            int totalMaxMarks,
            int totalMarksAwarded,
            String evaluatedQuestionsJson,
            Instant createdAt
    ) {
        this.job = job;
        this.studentName = studentName;
        this.studentId = studentId;
        this.subject = subject;
        this.classAndSection = classAndSection;
        this.date = date;
        this.totalMaxMarks = totalMaxMarks;
        this.totalMarksAwarded = totalMarksAwarded;
        this.evaluatedQuestionsJson = evaluatedQuestionsJson;
        this.createdAt = createdAt;
    }


    public Job getJob() {
        return job;
    }


    public void setJob(Job job) {
        this.job = job;
    }

    public String getStudentName() {
		return studentName;
	}

	public void setStudentName(String studentName) {
		this.studentName = studentName;
	}

	public String getStudentId() {
		return studentId;
	}

	public void setStudentId(String studentId) {
		this.studentId = studentId;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getClassAndSection() {
		return classAndSection;
	}

	public void setClassAndSection(String classAndSection) {
		this.classAndSection = classAndSection;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public int getTotalMaxMarks() {
		return totalMaxMarks;
	}

	public void setTotalMaxMarks(int totalMaxMarks) {
		this.totalMaxMarks = totalMaxMarks;
	}

	public int getTotalMarksAwarded() {
		return totalMarksAwarded;
	}

	public void setTotalMarksAwarded(int totalMarksAwarded) {
		this.totalMarksAwarded = totalMarksAwarded;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setEvaluatedQuestionsJson(String evaluatedQuestionsJson) {
		this.evaluatedQuestionsJson = evaluatedQuestionsJson;
	}

	public Long getId() {
        return id;
    }

    public String getEvaluatedQuestionsJson() {
        return evaluatedQuestionsJson;
    }
    
    
    
    
}