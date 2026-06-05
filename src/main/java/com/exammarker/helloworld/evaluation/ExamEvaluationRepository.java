package com.exammarker.helloworld.evaluation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamEvaluationRepository
        extends JpaRepository<ExamEvaluationEntity, Long> {
	
	List<ExamEvaluationEntity> findAllByOrderByCreatedAtDesc();
}