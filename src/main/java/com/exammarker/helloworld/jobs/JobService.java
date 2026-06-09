package com.exammarker.helloworld.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.ExamEvaluationService;

import jakarta.transaction.Transactional;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final PaperFileRepository paperFileRepository;
    private final ExamEvaluationService examEvaluationService;
    private final Executor evaluationExecutor;
    

	public JobService(JobRepository jobRepository, PaperFileRepository paperFileRepository,
			ExamEvaluationService examEvaluationService, Executor evaluationExecutor) {

		this.jobRepository = jobRepository;
		this.paperFileRepository = paperFileRepository;
		this.examEvaluationService = examEvaluationService;
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
            paper.setJob(job); // <-- SINGLE POINT OF TRUTH
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
                            () -> examEvaluationService.processPaper(job, paper, rubricBytes, solutionBytes),
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

    
    
    @Transactional
    public Long startJob(
            List<MultipartFile> papers,
            List<MultipartFile> rubric,
            List<MultipartFile> solution
    ) throws IOException {

 
        // upload paperfiles
        Job job = uploadAndCreateJob(papers);
        
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