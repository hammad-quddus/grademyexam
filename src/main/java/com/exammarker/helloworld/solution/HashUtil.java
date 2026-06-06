package com.exammarker.helloworld.solution;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public class HashUtil {

	public static String computeHash(byte[] fileBytes) {
	    try {
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");

	        byte[] hashBytes = digest.digest(fileBytes);

	        StringBuilder sb = new StringBuilder();
	        for (byte b : hashBytes) {
	            sb.append(String.format("%02x", b));
	        }

	        return sb.toString();

	    } catch (Exception e) {
	        throw new RuntimeException("Failed to compute hash", e);
	    }
	}

//    private static byte[] readBytes(MultipartFile file) {
//        try (InputStream is = file.getInputStream()) {
//            return is.readAllBytes();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to read file", e);
//        }
//    }
}