package com.exammarker.helloworld.jobs;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final PaperFileRepository paperFileRepository;

    public JobService(JobRepository jobRepository,
                      PaperFileRepository paperFileRepository) {
        this.jobRepository = jobRepository;
        this.paperFileRepository = paperFileRepository;
    }
    
    public Job createJob() {
        Job job = new Job();
        job.setStatus(JobStatus.ACCEPTED);
        return jobRepository.save(job);
    }    
    
//    public void uploadPaper(Long jobId, MultipartFile file) {
//
//        Job job = jobRepository.findById(jobId)
//                .orElseThrow(() -> new RuntimeException("Job not found"));
//
//        PaperFile paper = new PaperFile();
//
//        paper.setOriginalFilename(file.getOriginalFilename());
////        paper.setStoragePath(saveFileSomewhere(file));
//        paper.setContentType(file.getContentType());
//
//        paper.setJob(job); // <-- SINGLE POINT OF TRUTH
//
//        paperFileRepository.save(paper);
//    }

    public void uploadPapers(Job job, List<MultipartFile> files) {
//
//        Job job = jobRepository.findById(jobId)
//                .orElseThrow(() -> new RuntimeException("Job not found"));
//
        
        files.forEach(file -> {
            PaperFile paper = new PaperFile();

            paper.setOriginalFilename(file.getOriginalFilename());
            paper.setContentType(file.getContentType());
            paper.setJob(job); // <-- SINGLE POINT OF TRUTH
            paperFileRepository.save(paper);
        	
        });
    }
    
    public List<PaperFileDto> getPapers(Long jobId) {

        return paperFileRepository.findByJobId(jobId)
                .stream()
                .map(PaperFileDto::from)
                .toList();
    }



}