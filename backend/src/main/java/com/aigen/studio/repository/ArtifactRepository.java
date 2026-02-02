package com.aigen.studio.repository;

import com.aigen.studio.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    List<Artifact> findByJobId(Long jobId);

    @Query("SELECT a FROM Artifact a WHERE a.jobId = :jobId AND a.type = :type")
    List<Artifact> findByJobIdAndType(@Param("jobId") Long jobId, @Param("type") String type);

    void deleteByJobId(Long jobId);
}