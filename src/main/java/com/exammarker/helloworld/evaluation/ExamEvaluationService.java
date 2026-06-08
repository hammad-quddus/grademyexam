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
import com.exammarker.helloworld.jobs.Job;
import com.exammarker.helloworld.jobs.JobRepository;
import com.exammarker.helloworld.jobs.JobService;
import com.exammarker.helloworld.jobs.JobStatus;
import com.exammarker.helloworld.service.GradingService;

import jakarta.transaction.Transactional;

@Service
public class ExamEvaluationService {

    private final ExamEvaluationRepository repository;
    private final JobRepository jobRepository;
    private final Executor evaluationExecutor;
    private final GradingService gradingService;
    private final JobService jobService;

    public ExamEvaluationService(
            ExamEvaluationRepository repository,
            JobRepository jobRepository,
            Executor evaluationExecutor,
            GradingService gradingService,
            JobService jobService
    ) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.evaluationExecutor = evaluationExecutor;
        this.gradingService = gradingService;
        this.jobService = jobService;
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

        Job job = new Job();
        job = jobRepository.save(job);

        for (ExamEvaluationDto dto : dtos) {

            ExamEvaluation entity =
                    ExamEvaluationMapper.toEntity(dto, job);

            repository.save(entity);
        }

        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);

        return job.getId();
    }
    
    
    public ExamEvaluationDto getById(Long id) {

        ExamEvaluation entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evaluation not found: " + id));

        return ExamEvaluationMapper.toDto(entity);
    }
    
    public List<ExamEvaluationSummaryDto> getAllEvaluations() {

        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ExamEvaluationMapper::toSummaryDto)
                .toList();
    }
    
 
    @Transactional
    public Long startJob(
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

        Job job = new Job();
        job.setStatus(JobStatus.ACCEPTED);
        job = jobRepository.save(job);

        job.setStatus(JobStatus.PROCESSING);
        jobRepository.save(job);

        // upload paperfiles
        jobService.uploadPapers(job, papers);
        
        processAsync(job, papers, rubric, solution);

        return job.getId();
    }
    
    private void processAsync(
            Job job,
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

            repository.save(entity);

        } catch (Exception e) {
            throw new RuntimeException("Paper processing failed", e);
        }
    }   
}