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

    private static final PDType1Font FONT =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private final PDDocument document;

    private PDPage page;
    private PDPageContentStream content;

    private float y = 750;

    private static final float LEFT = 50;

    // track all streams safely
    private final List<PDPageContentStream> streams = new ArrayList<>();

    public PdfLayoutEngine(PDDocument document) throws IOException {
        this.document = document;
        newPage();
    }

    // ---------------- PUBLIC API ----------------

    public void title(String value) throws IOException {
        write(value, 16);
        gap(20);
    }

    public void section(String heading, String body) throws IOException {
        write(heading, 12);
        write(body == null ? "" : body, 10);
        gap(15);
    }

    public void list(String heading, List<String> items) throws IOException {

        if (items == null || items.isEmpty()) return;

        write(heading, 12);

        for (String item : items) {
            write("- " + item, 10);
        }

        gap(15);
    }

    // ---------------- CORE WRITE ----------------

    private void write(String text, float size) throws IOException {

        List<String> lines = wrap(text, size);

        for (String line : lines) {

            checkPage(size);

            content.beginText();
            content.setFont(FONT, size);
            content.newLineAtOffset(LEFT, y);
            content.showText(line);
            content.endText();

            y -= size + 5;
        }
    }

    private List<String> wrap(String text, float size) {

        int charsPerLine = size >= 14 ? 50 : 85;

        String cleaned = (text == null) ? "" : text.replace("\n", " ");

        return List.of(cleaned.split("(?<=\\G.{" + charsPerLine + "})"));
    }

    // ---------------- PAGE MANAGEMENT ----------------

    private void checkPage(float size) throws IOException {

        if (y < 70) {
            closeCurrentStream();
            newPage();
        }
    }

    private void newPage() throws IOException {

        page = new PDPage();
        document.addPage(page);

        content = new PDPageContentStream(document, page);
        streams.add(content);

        y = 750;
    }

    private void closeCurrentStream() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
    }

    private void gap(float amount) {
        y -= amount;
    }

    private float getPageWidth() {
        return page.getMediaBox().getWidth();
    }
    
    public void line() throws IOException {

        checkPage(0);

        float right = getPageWidth() - LEFT;

        content.moveTo(LEFT, y);
        content.lineTo(right, y);
        content.stroke();

        y -= 10;
    }
    
    // ---------------- FINALIZATION ----------------

    public void close() throws IOException {

        // close active stream first
        closeCurrentStream();

        // safety: close any orphan streams
        for (PDPageContentStream s : streams) {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception ignored) {}
            }
        }

        streams.clear();
    }
}