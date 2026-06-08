package com.exammarker.helloworld.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperFileRepository extends JpaRepository<PaperFile, Long> {

    List<PaperFile> findByJobId(Long jobId);
}