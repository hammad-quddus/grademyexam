package com.exammarker.helloworld.service;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

@Service
public class PdfAssemblyService {

    private static final Logger log =
            LoggerFactory.getLogger(PdfAssemblyService.class);

    private Instant extractCreationTime(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {

            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            ExifSubIFDDirectory directory =
                    metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (directory != null) {

                Date date = directory.getDateOriginal();

                if (date != null) {
                    log.debug("EXIF creation time: {}", date.toInstant());
                    return date.toInstant();
                }
            }

        } catch (Exception e) {
            log.warn("Failed to read EXIF metadata for file: {}", file.getOriginalFilename(), e);
        }

        return Instant.now();
    }

    public byte[] imagesToPdf(List<MultipartFile> images) throws IOException {
        // ---------------- PDF PASSTHROUGH ----------------

        if (images.size() == 1) {

            MultipartFile file = images.get(0);

            boolean isPdf =
                    "application/pdf".equalsIgnoreCase(file.getContentType())
                    || file.getOriginalFilename()
                            .toLowerCase()
                            .endsWith(".pdf");

            if (isPdf) {

                log.info("PDF detected. Returning original bytes directly.");

                return file.getBytes();
            }
        }

        // ---------------- IMAGE → PDF ----------------

        images.sort(
                Comparator.comparing(this::extractCreationTime)
                        .thenComparing(MultipartFile::getOriginalFilename)
        );

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            for (MultipartFile file : images) {

                BufferedImage original =
                        ImageIO.read(file.getInputStream());

                if (original == null) {
                    log.warn("ImageIO failed to decode file: {}", file.getOriginalFilename());
                    continue;
                }

                BufferedImage resized = resizeImage(original, 1400);

                PDPage page = new PDPage(
                        new PDRectangle(
                                resized.getWidth(),
                                resized.getHeight()
                        )
                );

                document.addPage(page);

                PDImageXObject pdImage =
                        JPEGFactory.createFromImage(document, resized, 0.7f);

                try (PDPageContentStream contentStream =
                             new PDPageContentStream(document, page)) {

                    contentStream.drawImage(
                            pdImage,
                            0,
                            0,
                            resized.getWidth(),
                            resized.getHeight()
                    );
                }
            }

            document.save(outputStream);

            log.info("PDF generated successfully. Pages: {}", images.size());

            return outputStream.toByteArray();
        }
    }

    private static final int MAX_DIMENSION = 1400;
    private static final long MAX_PIXELS = 2_000_000;

    private BufferedImage resizeImage(BufferedImage original, int ignoredMaxWidth) {

        if (original == null) {
            throw new IllegalArgumentException("ImageIO returned null BufferedImage");
        }

        int width = original.getWidth();
        int height = original.getHeight();

        log.debug("resizeImage input: {}x{}", width, height);

        double scaleForWidth = (double) MAX_DIMENSION / width;
        double scaleForHeight = (double) MAX_DIMENSION / height;

        double scale = Math.min(scaleForWidth, scaleForHeight);

        long pixels = (long) width * height;

        if (pixels > MAX_PIXELS) {
            double pixelScale = Math.sqrt((double) MAX_PIXELS / pixels);
            scale = Math.min(scale, pixelScale);
        }

        if (scale >= 1.0) {
            log.debug("No resize needed (safe image: {}x{})", width, height);
            return ensureRGB(original);
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        log.debug("Resizing image from {}x{} → {}x{}",
                width, height, newWidth, newHeight);

        Image scaled = original.getScaledInstance(
                newWidth,
                newHeight,
                Image.SCALE_SMOOTH
        );

        BufferedImage resized = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = resized.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();

        return resized;
    }

    private BufferedImage ensureRGB(BufferedImage image) {

        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage rgb = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return rgb;
    }
}