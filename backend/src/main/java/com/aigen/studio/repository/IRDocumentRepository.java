package com.aigen.studio.repository;

import com.aigen.studio.entity.IRDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IRDocumentRepository extends JpaRepository<IRDocument, Long> {

    Optional<IRDocument> findByRequirementId(Long requirementId);

    List<IRDocument> findByStatus(IRDocument.IRStatus status);

    void deleteByRequirementId(Long requirementId);
}