package com.exammarker.helloworld.solution;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public class HashUtil {

    public static String computeHash(List<MultipartFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Step 1: normalize order (VERY important)
            List<byte[]> fileBytes = files.stream()
                    .map(HashUtil::readBytes)
                    .toList();

            // Step 2: sort to ensure order independence
            fileBytes = fileBytes.stream()
                    .sorted(Arrays::compare)
                    .toList();

            // Step 3: feed into digest
            for (byte[] bytes : fileBytes) {
                digest.update(bytes);
            }

            byte[] hashBytes = digest.digest();

            // Step 4: convert to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }
}