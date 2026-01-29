package com.aigen.studio.repository;

import com.aigen.studio.entity.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByJobCode(String jobCode);

    List<GenerationJob> findByRequirementId(Long requirementId);

    List<GenerationJob> findByStatus(GenerationJob.JobStatus status);

    boolean existsByJobCode(String jobCode);
}