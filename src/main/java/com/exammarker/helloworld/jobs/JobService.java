package com.exammarker.helloworld.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.EvaluationService;

import jakarta.transaction.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final PaperFileRepository paperFileRepository;
    private final EvaluationService evaluationService; 
    private final Executor evaluationExecutor;

    public JobService(JobRepository jobRepository, PaperFileRepository paperFileRepository,
                      EvaluationService evaluationService, Executor evaluationExecutor) {
        this.jobRepository = jobRepository;
        this.paperFileRepository = paperFileRepository;
        this.evaluationService = evaluationService;
        this.evaluationExecutor = evaluationExecutor;
    }

    public Job createJob() {
        Job job = new Job();
        job.setStatus(JobStatus.ACCEPTED);
        return jobRepository.save(job);
    }    

    public void uploadPapers(Job job, List<MultipartFile> files) {
        files.forEach(file -> {
            PaperFile paper = new PaperFile();
            paper.setOriginalFilename(file.getOriginalFilename());
            paper.setContentType(file.getContentType());
            paper.setJob(job); // SINGLE POINT OF TRUTH
            paperFileRepository.save(paper);
        });
    }

    public Job uploadAndCreateJob(List<MultipartFile> files) {
        Job job = createJob();
        uploadPapers(job, files);
        return job;
    }    

    public List<PaperFileDto> getPapers(Long jobId) {
        return paperFileRepository.findByJobId(jobId)
                .stream()
                .map(PaperFileDto::from)
                .toList();
    }

    public void processAsync(
            Job job,
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

    	job.setStatus(JobStatus.PROCESSING);
    	jobRepository.save(job);
    	
        log.info("Preparing batch upload bytes for Job ID: {} before entering async boundary...", job.getId());

        // Convert files to byte arrays once BEFORE crossing the async boundary
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
        
        // Track if any individual paper has completely failed during background processing
        AtomicBoolean anyPaperFailed = new AtomicBoolean(false);

        // Loop over papers and execute each paper exactly ONCE concurrently using the background executor
        for (int i = 0; i < paperBytes.size(); i++) {
            final byte[] paper = paperBytes.get(i);
            final int paperIndex = i + 1;
            
            futures.add(
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Starting background execution for paper {}/{} of Job ID: {}", paperIndex, paperBytes.size(), job.getId());
                        
                        // Add a tiny incremental delay (jitter) to stagger concurrent API requests and mitigate 429 Resource Exhausted errors
                        Thread.sleep((paperIndex - 1) * 1500L);
                        
                        // Invokes the synchronous EvaluationService to run the sequential grading pipeline on this dedicated thread
                        evaluationService.processPaper(job, paper, rubricBytes, solutionBytes);
                        
//                        evaluateEntireExamPipeline(paper, rubricBytes, solutionBytes);
                        
                        log.info("Successfully graded paper {}/{} of Job ID: {}", paperIndex, paperBytes.size(), job.getId());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Paper processing thread was interrupted for Job ID: {}", job.getId(), ie);
                        anyPaperFailed.set(true);
                    } catch (Exception e) {
                        // FAULT ISOLATION: Catch exceptions here to prevent one failing paper from crashing the whole batch
                        log.error("Failed processing paper {}/{} in background thread for Job ID: {}. Error: {}", 
                                paperIndex, paperBytes.size(), job.getId(), e.getMessage(), e);
                        anyPaperFailed.set(true);
                        // Do not rethrow exception to CompletableFuture, so allOf barrier can proceed successfully
                    }
                }, evaluationExecutor)
            );
        }

        // Barrier pattern waiting for all parallel student paper processes to complete
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((res, ex) -> {
                    // Update job status based on the atomic flag to ensure accurate state reporting
                    if (ex != null || anyPaperFailed.get()) {
                        log.warn("Job ID: {} completed, but some papers failed processing.", job.getId());
                        job.setStatus(JobStatus.FAILED);
                    } else {
                        log.info("Job ID: {} completed successfully.", job.getId());
                        job.setStatus(JobStatus.COMPLETED);
                    }
                    jobRepository.save(job);
                });
    }


    public Long startJob(
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

        // Upload paper files and persist Job metadata
        Job job = uploadAndCreateJob(papers);
        
        // Dispatch to background processing
        processAsync(job, papers, rubric, solution);

        return job.getId();
    }

    public JobDetailDto getJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        List<PaperFileDto> papers = paperFileRepository.findByJobId(jobId)
                .stream()
                .map(PaperFileDto::from)
                .toList();

        JobDetailDto dto = new JobDetailDto();
        dto.setId(job.getId());
        dto.setStatus(job.getStatus());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setPapers(papers);

        return dto;
    }    
}