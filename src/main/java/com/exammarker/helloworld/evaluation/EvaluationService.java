package com.exammarker.helloworld.evaluation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.jobs.Job;
import com.exammarker.helloworld.service.GradingService;

import jakarta.transaction.Transactional;

@Service
public class EvaluationService {

    private final ExamEvaluationRepository examEvaluationRepository;
    private final GradingService gradingService;

    
    
    
    public EvaluationService(ExamEvaluationRepository examEvaluationRepository, 
    		GradingService gradingService) {

		this.examEvaluationRepository = examEvaluationRepository;
		this.gradingService = gradingService;
	}

	public ExamEvaluationDto getById(Long id) {

        ExamEvaluation entity = examEvaluationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evaluation not found: " + id));

        return ExamEvaluationMapper.toDto(entity);
    }
    
    public List<ExamEvaluationSummaryDto> getAllEvaluations() {

        return examEvaluationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ExamEvaluationMapper::toSummaryDto)
                .toList();
    }   
    
    @Transactional
    public void processPaper(
            Job job,
            byte[] paper,
            byte[] rubric,
            byte[] solution
    ) {

        try {
            ExamEvaluationDto dto =
                    gradingService.evaluateEntireExamPipeline(
                            paper,
                            rubric,
                            solution
                    );

            ExamEvaluation entity =
                    ExamEvaluationMapper.toEntity(dto, job);

            examEvaluationRepository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Paper processing failed", e);
        }
    }
    
    
    
}