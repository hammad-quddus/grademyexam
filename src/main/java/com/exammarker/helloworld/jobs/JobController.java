package com.exammarker.helloworld.jobs;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
	
	@GetMapping("/{id}")
	public JobDetailDto getJob(@PathVariable Long id) {
	    return jobService.getJob(id);
	}
	
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Long>> evaluate(
	        @RequestPart("papers") List<MultipartFile> papers,
	        @RequestPart("rubric") List<MultipartFile> rubric,
	        @RequestPart("solution") List<MultipartFile> solution
	) throws IOException {

	    Long jobId = jobService.startJob(papers, rubric, solution);

	    return ResponseEntity
	            .accepted()
	            .body(Map.of("jobId", jobId));
	}

	
	
	
}