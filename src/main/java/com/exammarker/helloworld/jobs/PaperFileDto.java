package com.exammarker.helloworld.jobs;


public class PaperFileDto {

    private Long id;
    private String originalFilename;
    private String contentType;


    public static PaperFileDto from(PaperFile paper) {

        PaperFileDto dto = new PaperFileDto();

        dto.id = paper.getId();
        dto.originalFilename = paper.getOriginalFilename();
        dto.contentType = paper.getContentType();

        return dto;
    }


    public Long getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }
}