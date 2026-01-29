package com.aigen.studio.repository;

import com.aigen.studio.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    List<Artifact> findByJobId(Long jobId);

    List<Artifact> findByJobIdAndType(Long jobId, Artifact.ArtifactType type);

    void deleteByJobId(Long jobId);
}