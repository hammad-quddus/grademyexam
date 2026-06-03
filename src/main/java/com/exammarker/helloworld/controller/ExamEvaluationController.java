package com.exammarker.helloworld.controller;

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
import com.exammarker.helloworld.evalutation.dto.ExamEvaluationDto;
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
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long id
    ) {

        ExamEvaluationDto dto =
                examEvaluationService.getById(id);


        byte[] pdf =
                examPdfService.generate(dto);


        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                  "Content-Disposition",
                  "attachment; filename=evaluation-"+id+".pdf"
                )
                .body(pdf);
    }
    
}