package com.exammarker.helloworld.service.pdf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import com.exammarker.helloworld.evaluation.dto.ExamEvaluationDto;
import com.exammarker.helloworld.evaluation.dto.QuestionEvaluationDto;

@Service
public class ExamPdfService {

    public byte[] generate(ExamEvaluationDto dto) {

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PdfLayoutEngine pdf = new PdfLayoutEngine(document);

            pdf.title("Exam Evaluation Report");

            pdf.section("Student", dto.studentName());
            pdf.section("Subject", dto.subject());
            pdf.section("Total Marks", dto.totalMarksAwarded() + " / " + dto.totalMaxMarks());

            // ---------------- MARKS TABLE ----------------
            pdf.section("Marks Breakdown (Question-wise)", "");

            List<List<String>> table = new ArrayList<>();

            table.add(List.of("Question", "Marks", "Max Marks", "Band Range"));

            for (QuestionEvaluationDto q : dto.evaluatedQuestions()) {
                table.add(List.of(
                        String.valueOf(q.questionId()),
                        String.valueOf(q.marksAwarded()),
                        String.valueOf(q.maxMarks()),
                        q.rubricReference().band().min() + " - " + q.rubricReference().band().max()
                ));
            }

            pdf.table(table);

            pdf.line();

            // ---------------- DETAILED BREAKDOWN ----------------
            for (QuestionEvaluationDto q : dto.evaluatedQuestions()) {

                pdf.section("Question " + q.questionId(), q.questionText());

                pdf.section("Marks",
                        q.marksAwarded() + " / " + q.maxMarks());

                pdf.section("Evaluation Summary", q.evaluationSummary());

                pdf.list("Coverage Gaps", q.coverageGaps());
                pdf.list("Factual Errors", q.factualErrors());

                pdf.section("Student Answer", q.studentAnswerTranscription());

                pdf.line();
            }

            pdf.close();
            document.save(out);

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }
}