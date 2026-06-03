package com.exammarker.helloworld.service.pdf;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import com.exammarker.helloworld.evalutation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evalutation.dto.QuestionEvaluationDto;

@Service
public class ExamPdfService {

	public byte[] generate(ExamEvaluationDto dto) {

		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			PdfLayoutEngine pdf = new PdfLayoutEngine(document);

			pdf.title("Exam Evaluation Report");

			pdf.section("Student", dto.studentName());
			pdf.section("Subject", dto.subject());

			pdf.section("Total Marks", dto.totalMarksAwarded() + " / " + dto.totalMaxMarks());

			for (QuestionEvaluationDto q : dto.evaluatedQuestions()) {

				pdf.section("Question " + q.questionId(), q.questionText());

				pdf.section("Marks Awarded", String.valueOf(q.marksAwarded()) + " / " + q.maxMarks());

				pdf.section("Evaluation Summary", q.evaluationSummary());

				pdf.list("Coverage Gaps", q.coverageGaps());

				pdf.list("Factual Errors", q.factualErrors());

				pdf.section("Student Answer", q.studentAnswerTranscription());
				
				pdf.line();
				
			}
			// IMPORTANT: flush all streams properly
			pdf.close();

			document.save(out);

			return out.toByteArray();

		} catch (Exception e) {
			throw new RuntimeException("PDF generation failed", e);
		}
	}
}