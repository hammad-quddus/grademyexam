package com.exammarker.helloworld.evaluation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;

@Service
public class ExamEvaluationService {

    private final ExamEvaluationRepository repository;
    

    public ExamEvaluationService(ExamEvaluationRepository repository) {
        this.repository = repository;
    }

//    public Long saveEvaluation(ExamEvaluationEntity entity) {
//        return repository.save(entity).getId();
//    }

//    public ExamEvaluationEntity getById(Long id) {
//        return repository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Evaluation not found: " + id));
//    }
    
    public Long saveEvaluation(ExamEvaluationDto dto) {

        ExamEvaluationEntity entity =
                ExamEvaluationMapper.toEntity(dto);

        return repository.save(entity).getId();
    }

    public ExamEvaluationDto getById(Long id) {

        ExamEvaluationEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evaluation not found: " + id));

        return ExamEvaluationMapper.toDto(entity);
    }
    
    public List<ExamEvaluationSummaryDto> getAllEvaluations() {

        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ExamEvaluationMapper::toSummaryDto)
                .toList();
    }
    
}