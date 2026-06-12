package com.exammarker.helloworld.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.exammarker.helloworld.evaluation.EvaluationService;
import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evalutation.dto.rubric.RubricDto;
import com.exammarker.helloworld.service.LegacyGradingService;
import com.exammarker.helloworld.service.PdfAssemblyService;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private static final Logger log =
            LoggerFactory.getLogger(PdfAssemblyService.class);
    
    
    private final LegacyGradingService legacyGradingService;
    private final EvaluationService examEvaluationService;


    public ExamController(LegacyGradingService legacyGradingService, EvaluationService examEvaluationService) {
        this.legacyGradingService = legacyGradingService;
        this.examEvaluationService = examEvaluationService;

    }
    
    
//    @PostMapping(value = "/transcribestudentpaper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public TranscribedExamDto transcribeStudentpaper(
//
//    		@RequestPart(value = "studentpaper", required = true) List<MultipartFile> studentpaperImages
//
//    ) throws Exception {
//
//    	log.info("endpoint: transcribestudentpaper...");
// 
//    	return gradingService.transcribeAndSegmentPaper(studentpaperImages);
//
//    }
//    
//    
//    @PostMapping(value = "/transcribesolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public TranscribedSolutionsDto transcribeSolution(
//
//    		@RequestPart(value = "solution", required = true) List<MultipartFile> solutionImages
//
//    ) throws Exception {
//
//    	log.info("endpoint: transcribesolution...");
// 
//    	return legacyGradingService.transcribeOfficialSolutions(solutionImages);
//
//    }
   
    
    @PostMapping(value = "/transcriberubric", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RubricDto transcribeRubric(

    		@RequestPart(value = "rubric", required = true) List<MultipartFile> rubricImages

    ) throws Exception {

    	log.info("endpoint: transcriberubric...");
 
    	return legacyGradingService.transcribeRubric(rubricImages);

    }
    
//    @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public QuestionEvaluationDto evaluate(
//            @RequestPart(value = "paperImages", required = true) List<MultipartFile> paperImages,
//            @RequestPart(value = "rubricImages", required = true) List<MultipartFile> rubricImages,
//            @RequestPart(value = "solutionImages", required = true) List<MultipartFile> solutionImages
//    ) throws Exception {
//    	
//    	log.info("endpoint: evaluat...");
//    	log.info("total paperImages: " + paperImages.size());
//    	log.info("total rubricImages: " + rubricImages.size());
//    	log.info("total solutionImages: " + solutionImages.size());
//    	
//    	
//    	return gradingService.evaluateQuestion(paperImages, rubricImages, solutionImages);
//   }    
    
    
//    @PostMapping(value = "/evaluatefullexam", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ExamEvaluationResponse evaluateFullExam(
//            @RequestPart(value = "paperImages", required = true) List<MultipartFile> paperImages,
//            @RequestPart(value = "rubricImages", required = true) List<MultipartFile> rubricImages,
//            @RequestPart(value = "solutionImages", required = true) List<MultipartFile> solutionImages
//    ) throws Exception {
//    	
//    	log.info("endpoint: evaluatefullexam...");
//    	log.info("total paperImages: " + paperImages.size());
//    	log.info("total rubricImages: " + rubricImages.size());
//    	log.info("total solutionImages: " + solutionImages.size());
//    	
//    	
//    	ExamEvaluationDto dto =  gradingService.evaluateEntireExamPipeline(paperImages, rubricImages, solutionImages);
//    	long id = examEvaluationService.createJobAndSaveEvaluations(dtos);
//    	
//    	return new ExamEvaluationResponse(id, dto);
//   }        
    
}

record ExamEvaluationResponse(
        Long id,
        ExamEvaluationDto evaluation
) {}
