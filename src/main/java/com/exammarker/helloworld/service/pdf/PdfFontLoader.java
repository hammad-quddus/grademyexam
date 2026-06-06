package com.exammarker.helloworld.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.InputStream;

public class PdfFontLoader {

    public record FontBundle(PDFont regular, PDFont bold) {}

    public static FontBundle load(PDDocument document) {

        try {
            InputStream regularStream =
                    PdfFontLoader.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf");

            InputStream boldStream =
                    PdfFontLoader.class.getResourceAsStream("/fonts/NotoSans-Bold.ttf");

            PDFont regular = PDType0Font.load(document, regularStream);
            PDFont bold = PDType0Font.load(document, boldStream);

            return new FontBundle(regular, bold);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load fonts", e);
        }
    }
}