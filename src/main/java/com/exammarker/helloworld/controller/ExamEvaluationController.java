package com.exammarker.helloworld.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.ExamEvaluationService;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.service.GradingService;
import com.exammarker.helloworld.service.PdfAssemblyService;
import com.exammarker.helloworld.service.pdf.ExamPdfService;

@RestController
@RequestMapping("/evaluations")
public class ExamEvaluationController {

	
    private static final Logger log =
            LoggerFactory.getLogger(ExamEvaluationController.class);
	
	private final ExamEvaluationService examEvaluationService;
	private final ExamPdfService examPdfService;
	private final GradingService gradingService;

	public ExamEvaluationController(ExamEvaluationService examEvaluationService, ExamPdfService examPdfService, GradingService gradingService) {
		this.examEvaluationService = examEvaluationService;
		this.examPdfService = examPdfService;
		this.gradingService = gradingService;
	}

//	@PostMapping
//	public ResponseEntity<Map<String, Long>> create(@RequestBody List<ExamEvaluationDto> dtos) {
//
//		Long id = examEvaluationService.createJobAndSaveEvaluations(dtos);
//
//		return ResponseEntity
//				.accepted()
//				.body(Map.of("jobId", id));
//	}
	

	@GetMapping("/{id}")
	public ResponseEntity<ExamEvaluationDto> get(@PathVariable Long id) {
		return ResponseEntity.ok(examEvaluationService.getById(id));
	}

	@GetMapping("/{id}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable Long id) {

		ExamEvaluationDto dto = examEvaluationService.getById(id);

		byte[] pdf = examPdfService.generate(dto);

		String studentName = dto.studentName().replaceAll("[^a-zA-Z0-9]", "_");

		String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd_MM_yyyy"));

		String fileName = id + "_" + studentName + "_" + date + ".pdf";

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header("Content-Disposition", "attachment; filename=" + fileName).body(pdf);
	}
	
	
//	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//	public ResponseEntity<Map<String, Long>> evaluateFullExams(
//	        @RequestPart("paperImages") List<MultipartFile> paperPdfs,
//	        @RequestPart("rubricImages") List<MultipartFile> rubricPdf,
//	        @RequestPart("solutionImages") List<MultipartFile> solutionPdf
//	) throws Exception {
//
//	    log.info("Processing {} papers", paperPdfs.size());
//
//	    List<ExamEvaluationDto> dtos = new ArrayList<>();
//
//	    paperPdfs.forEach(paper -> {
//	        try {
//	            ExamEvaluationDto dto =
//	                    gradingService.evaluateEntireExamPipeline(
//	                            List.of(paper),
//	                            rubricPdf,
//	                            solutionPdf
//	                    );
//
//	            dtos.add(dto);
//
//	        } catch (Exception e) {
//	            log.error("Failed processing paper", e);
//	        }
//	    });
//
//	    Long jobId = examEvaluationService.createJobAndSaveEvaluations(dtos);
//
//	    return ResponseEntity
//	            .accepted()
//	            .body(Map.of("jobId", jobId));
//	}
	
	
	
	@GetMapping
	public ResponseEntity<List<ExamEvaluationSummaryDto>> getAll() {
	    return ResponseEntity.ok(examEvaluationService.getAllEvaluations());
	}
	
	
//	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//	public ResponseEntity<Map<String, Long>> evaluate(
//	        @RequestPart("paperImages") List<MultipartFile> papers,
//	        @RequestPart("rubricImages") List<MultipartFile> rubric,
//	        @RequestPart("solutionImages") List<MultipartFile> solution
//	) throws IOException {
//
//	    Long jobId = examEvaluationService.startJob(papers, rubric, solution);
//
//	    return ResponseEntity
//	            .accepted()
//	            .body(Map.of("jobId", jobId));
//	}

}