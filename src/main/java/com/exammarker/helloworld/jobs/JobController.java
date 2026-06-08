package com.exammarker.helloworld.jobs;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;


    public JobController(JobService jobService) {
		this.jobService = jobService;
	}


	@GetMapping("/{jobId}/papers")
    public List<PaperFileDto> getPapers(
            @PathVariable Long jobId) {

        return jobService.getPapers(jobId);
    }
	
	
	
	
	
}