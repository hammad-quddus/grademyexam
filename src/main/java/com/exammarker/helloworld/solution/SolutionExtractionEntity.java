package com.exammarker.helloworld.solution;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "solution_extraction")
public class SolutionExtractionEntity {

    @Id
    @Column(name = "document_hash", nullable = false, updatable = false)
    private String documentHash;

//    @ElementCollection
//    @CollectionTable(
//            name = "solution_extraction_files",
//            joinColumns = @JoinColumn(name = "document_hash")
//    )


    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    @Lob
    @Column(name = "extraction_json", nullable = false)
    private String extractionJson;

    protected SolutionExtractionEntity() {
        // JPA requirement
    }

    public SolutionExtractionEntity(
            String documentHash,

            Instant extractedAt,
            String extractionJson
    ) {
        this.documentHash = documentHash;
        this.extractedAt = extractedAt;
        this.extractionJson = extractionJson;
    }

    public String getDocumentHash() {
        return documentHash;
    }


    public Instant getExtractedAt() {
        return extractedAt;
    }

    public String getExtractionJson() {
        return extractionJson;
    }

    // Optional setters (keep or remove depending on immutability preference)

    public void setExtractedAt(Instant extractedAt) {
        this.extractedAt = extractedAt;
    }

    public void setExtractionJson(String extractionJson) {
        this.extractionJson = extractionJson;
    }
}