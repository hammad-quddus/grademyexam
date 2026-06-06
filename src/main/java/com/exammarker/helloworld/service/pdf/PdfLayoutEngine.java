package com.exammarker.helloworld.service.pdf;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PdfLayoutEngine {

    private final PDFont FONT;
    private final PDFont ITALIC; // optional usage

    private final PDDocument document;

    private PDPage page;
    private PDPageContentStream content;

    private float y;

    private static final float LEFT = 50;
    private static final float RIGHT_MARGIN = 550;
    private static final float TOP = 750;

    public PdfLayoutEngine(PDDocument document) throws IOException {
        this.document = document;

        this.FONT = loadFont("/fonts/NotoSans-Bold.ttf");
        this.ITALIC = loadFont("/fonts/NotoSans-Regular.ttf");

        newPage();
    }

    private PDFont loadFont(String path) throws IOException {
        InputStream stream = PdfLayoutEngine.class.getResourceAsStream(path);

        if (stream == null) {
            // fallback to PDFBox built-in font so nothing crashes
            return new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA
            );
        }

        return PDType0Font.load(document, stream, true);
    }

    // ---------------- TEXT ----------------

    public void title(String text) throws IOException {
        write(text, 18, FONT);
        gap(20);
    }

    public void section(String heading, String body) throws IOException {
        write(heading, 12, FONT);
        write(body == null ? "" : sanitize(body), 10, FONT);
        gap(10);
    }

    public void list(String heading, List<String> items) throws IOException {
        if (items == null || items.isEmpty()) return;

        write(heading, 12, FONT);

        for (String i : items) {
            write("• " + sanitize(i), 10, FONT);
        }

        gap(10);
    }

    // ---------------- LINE ----------------

    public void line() throws IOException {
        checkPage(10);

        content.setLineWidth(1f);
        content.moveTo(LEFT, y);
        content.lineTo(RIGHT_MARGIN, y);
        content.stroke();

        gap(15);
    }

    // ---------------- TABLE ----------------

    public void table(List<List<String>> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return;

        float rowHeight = 18;
        float colWidth = 120;

        for (List<String> row : rows) {
            checkPage(rowHeight);

            float x = LEFT;

            for (String cell : row) {
                content.beginText();
                content.setFont(FONT, 9);
                content.newLineAtOffset(x, y);
                content.showText(sanitize(cell == null ? "" : cell));
                content.endText();

                x += colWidth;
            }

            y -= rowHeight;
        }

        gap(10);
    }

    // ---------------- CORE WRITER ----------------

    private void write(String text, float size, PDFont font) throws IOException {
        for (String line : wrap(sanitize(text), size)) {
            checkPage(size + 5);

            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(LEFT, y);
            content.showText(line);
            content.endText();

            y -= size + 4;
        }
    }

    private List<String> wrap(String text, float size) {
        int chars = size >= 14 ? 60 : 90;

        return List.of(
                text.replace("\n", " ")
                        .split("(?<=\\G.{" + chars + "})")
        );
    }

    // ---------------- SANITIZE (IMPORTANT FIX) ----------------

    private String sanitize(String text) {
        if (text == null) return "";

        return text
                .replace("→", "->")
                .replace("–", "-")
                .replace("—", "-")
                .replace("•", "-")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("’", "'")
                .replace("√", "sqrt")
                .replace("×", "x");
    }

    // ---------------- PAGE HANDLING ----------------

    private void checkPage(float needed) throws IOException {
        if (y < 80) {
            content.close();
            newPage();
        }
    }

    private void newPage() throws IOException {
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        content = new PDPageContentStream(document, page);
        y = TOP;
    }

    public void gap(float amount) {
        y -= amount;
    }

    public void close() throws IOException {
        if (content != null) content.close();
    }
}