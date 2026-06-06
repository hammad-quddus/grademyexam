package com.exammarker.helloworld.evaluation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.jobs.JobEntity;
import com.exammarker.helloworld.jobs.JobRepository;
import com.exammarker.helloworld.jobs.JobStatus;
import com.exammarker.helloworld.service.GradingService;

@Service
public class ExamEvaluationService {

    private final ExamEvaluationRepository repository;
    private final JobRepository jobRepository;
    private final Executor evaluationExecutor;
    private final GradingService gradingService;

    public ExamEvaluationService(
            ExamEvaluationRepository repository,
            JobRepository jobRepository,
            Executor evaluationExecutor,
            GradingService gradingService
    ) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.evaluationExecutor = evaluationExecutor;
        this.gradingService = gradingService;
    }

//    public Long saveEvaluation(ExamEvaluationEntity entity) {
//        return repository.save(entity).getId();
//    }

//    public ExamEvaluationEntity getById(Long id) {
//        return repository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Evaluation not found: " + id));
//    }
    
//    public Long saveEvaluation(ExamEvaluationDto dto) {
//
//        ExamEvaluationEntity entity =
//                ExamEvaluationMapper.toEntity(dto);
//
//        return repository.save(entity).getId();
//    }

    public Long createJobAndSaveEvaluations(List<ExamEvaluationDto> dtos) {

        JobEntity job = new JobEntity();
        job = jobRepository.save(job);

        for (ExamEvaluationDto dto : dtos) {

            ExamEvaluationEntity entity =
                    ExamEvaluationMapper.toEntity(dto, job);

            repository.save(entity);
        }

        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        return job.getId();
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
    
    public Long startJob(
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

        JobEntity job = new JobEntity();
        job.setStatus(JobStatus.ACCEPTED);
        job = jobRepository.save(job);

        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);

        processAsync(job, papers, rubric, solution);

        return job.getId();
    }
    
    private void processAsync(
            JobEntity job,
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

        //  convert once BEFORE async boundary
        List<byte[]> paperBytes = papers.stream()
                .map(p -> {
                    try {
                        return p.getBytes();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to read paper file", e);
                    }
                })
                .toList();

        byte[] rubricBytes = rubric.get(0).getBytes();

        byte[] solutionBytes = solution.get(0).getBytes();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (byte[] paper : paperBytes) {

            futures.add(
                    CompletableFuture.runAsync(
                            () -> processPaper(job, paper, rubricBytes, solutionBytes),
                            evaluationExecutor
                    )
            );
        }

        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((res, ex) -> {

                    if (ex != null) {
                        job.setStatus(JobStatus.FAILED);
                    } else {
                        job.setStatus(JobStatus.COMPLETED);
                    }

                    jobRepository.save(job);
                });
    }
 
    private void processPaper(
            JobEntity job,
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

            ExamEvaluationEntity entity =
                    ExamEvaluationMapper.toEntity(dto, job);

            repository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Paper processing failed", e);
        }
    }   
}