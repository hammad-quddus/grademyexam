package com.exammarker.helloworld.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exammarker.helloworld.evaluation.ExamEvaluationService;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationSummaryDto;
import com.exammarker.helloworld.service.pdf.ExamPdfService;

@RestController
@RequestMapping("/evaluations")
public class ExamEvaluationController {

	private final ExamEvaluationService examEvaluationService;
	private final ExamPdfService examPdfService;

	public ExamEvaluationController(ExamEvaluationService examEvaluationService, ExamPdfService examPdfService) {
		this.examEvaluationService = examEvaluationService;
		this.examPdfService = examPdfService;
	}

	@PostMapping
	public ResponseEntity<Map<String, Long>> create(@RequestBody ExamEvaluationDto dto) {

		Long id = examEvaluationService.saveEvaluation(dto);

		return ResponseEntity.ok(Map.of("id", id));
	}

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
	
	@GetMapping
	public ResponseEntity<List<ExamEvaluationSummaryDto>> getAll() {
	    return ResponseEntity.ok(examEvaluationService.getAllEvaluations());
	}

}