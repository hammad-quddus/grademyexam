package com.exammarker.helloworld.service.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public class PdfLayoutEngine {

    private final PDDocument document;

    private PDPage page;
    private PDPageContentStream content;

    private float y = 750;

    private static final float LEFT = 50;
    private static final float RIGHT = 550;
    private static final float BOTTOM_LIMIT = 70;

    private static final float LINE_GAP = 5;

    private final PDType1Font font =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public PdfLayoutEngine(PDDocument document) throws IOException {
        this.document = document;
        newPage();
    }

    // ---------------- PUBLIC API ----------------
    
    public void gap(float amount) {
        y -= amount;
    }

    public void title(String text) throws IOException {
        write(text, 16);
        gap(15);
    }

    public void section(String heading, String body) throws IOException {
        write(heading, 12);
        write(safe(body), 10);
        gap(10);
    }

    public void list(String heading, List<String> items) throws IOException {

        if (items == null || items.isEmpty()) return;

        write(heading, 12);

        for (String item : items) {
            write("• " + safe(item), 10);
        }

        gap(10);
    }

    // ✔ divider line between questions
    public void line() throws IOException {

        ensureSpace(15);

        content.moveTo(LEFT, y);
        content.lineTo(RIGHT, y);
        content.stroke();

        y -= 12;
    }

    // ---------------- CORE WRITER ----------------

    private void write(String text, float size) throws IOException {

        List<String> lines = wrap(text, size);

        for (String line : lines) {

            ensureSpace(size);

            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(LEFT, y);
            content.showText(line);
            content.endText();

            y -= (size + LINE_GAP);
        }
    }

    // ---------------- PAGE SAFETY ----------------

    private void ensureSpace(float size) throws IOException {

        if (y < BOTTOM_LIMIT) {
            content.close();
            newPage();
        }
    }

    private void newPage() throws IOException {

        page = new PDPage();
        document.addPage(page);

        content = new PDPageContentStream(document, page);

        y = 750;
    }

    // ---------------- WRAPPING ----------------

    private List<String> wrap(String text, float size) {

        int charsPerLine = (size >= 14) ? 55 : 90;

        String clean = safe(text).replace("\n", " ");

        String[] split = clean.split("(?<=\\G.{" + charsPerLine + "})");

        List<String> lines = new ArrayList<>();
        for (String s : split) lines.add(s);

        return lines;
    }

    // ---------------- UTIL ----------------

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    // ---------------- CLOSE ----------------

    public void close() throws IOException {
        if (content != null) {
            content.close();
        }
    }
}