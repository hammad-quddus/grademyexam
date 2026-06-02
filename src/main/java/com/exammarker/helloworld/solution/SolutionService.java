package com.exammarker.helloworld.solution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SolutionService {

    private final SolutionRepository repository;

    public SolutionService(SolutionRepository repository) {
        this.repository = repository;
    }

    public SolutionExtractionEntity save(
            String hash,
            List<String> filenames,
            String json
    ) {
        SolutionExtractionEntity entity =
                new SolutionExtractionEntity(
                        hash,
                        filenames,
                        Instant.now(),
                        json
                );

        return repository.save(entity);
    }
    
    public SolutionExtractionEntity save(List<MultipartFile> files, String json) {
        String hash = HashUtil.computeHash(files);

        List<String> filenames = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .toList();

        return repository.findById(hash)
                .orElseGet(() -> save(
                        hash,
                        filenames,
                        json
                ));
    }

    public Optional<SolutionExtractionEntity> findByHash(String hash) {
        return repository.findById(hash);
    }

    public Optional<SolutionExtractionEntity> findByFiles(List<MultipartFile> files) {
    	String hash = HashUtil.computeHash(files);
        return repository.findById(hash);
    }

}