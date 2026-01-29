package com.aigen.studio.service;

import com.aigen.studio.dto.IRDocumentDTO;
import com.aigen.studio.entity.IRDocument;
import com.aigen.studio.repository.IRDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IRDocumentService {

    private final IRDocumentRepository irDocumentRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public IRDocumentDTO createIRDocument(Long requirementId, String content, String createdBy) {
        log.info("Creating IR document for requirement id: {}", requirementId);

        IRDocument irDocument = new IRDocument();
        irDocument.setRequirementId(requirementId);
        irDocument.setContent(content);
        irDocument.setStatus(IRDocument.IRStatus.DRAFT);
        irDocument.setCreatedBy(createdBy);
        irDocument.setUpdatedBy(createdBy);

        IRDocument saved = irDocumentRepository.save(irDocument);
        log.info("IR document created successfully with id: {}", saved.getId());

        return IRDocumentDTO.fromEntity(saved);
    }

    @Transactional
    public IRDocumentDTO updateIRDocument(Long id, String content, String updatedBy) {
        log.info("Updating IR document with id: {}", id);

        IRDocument irDocument = irDocumentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("IR document not found with id: " + id));

        irDocument.setContent(content);
        irDocument.setStatus(IRDocument.IRStatus.DRAFT);
        irDocument.setUpdatedBy(updatedBy);

        IRDocument saved = irDocumentRepository.save(irDocument);
        log.info("IR document updated successfully with id: {}", saved.getId());

        return IRDocumentDTO.fromEntity(saved);
    }

    @Transactional
    public IRDocumentDTO validateIRDocument(Long id) {
        log.info("Validating IR document with id: {}", id);

        IRDocument irDocument = irDocumentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("IR document not found with id: " + id));

        try {
            JsonNode jsonNode = objectMapper.readTree(irDocument.getContent());

            if (!jsonNode.has("projectName") || jsonNode.get("projectName").asText().isEmpty()) {
                throw new RuntimeException("Missing required field: projectName");
            }

            if (!jsonNode.has("modules") || !jsonNode.get("modules").isArray()) {
                throw new RuntimeException("Missing required field: modules");
            }

            irDocument.setStatus(IRDocument.IRStatus.VALID);
            irDocument.setValidationErrors(null);
            log.info("IR document validation passed for id: {}", id);

        } catch (Exception e) {
            irDocument.setStatus(IRDocument.IRStatus.INVALID);
            irDocument.setValidationErrors(e.getMessage());
            log.error("IR document validation failed for id: {}: {}", id, e.getMessage());
        }

        IRDocument saved = irDocumentRepository.save(irDocument);
        return IRDocumentDTO.fromEntity(saved);
    }

    public Optional<IRDocumentDTO> getIRDocumentById(Long id) {
        return irDocumentRepository.findById(id)
            .map(IRDocumentDTO::fromEntity);
    }

    public Optional<IRDocumentDTO> getIRDocumentByRequirementId(Long requirementId) {
        return irDocumentRepository.findByRequirementId(requirementId)
            .map(IRDocumentDTO::fromEntity);
    }

    public List<IRDocumentDTO> getAllIRDocuments() {
        return irDocumentRepository.findAll().stream()
            .map(IRDocumentDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteIRDocument(Long id) {
        log.info("Deleting IR document with id: {}", id);

        if (!irDocumentRepository.existsById(id)) {
            throw new RuntimeException("IR document not found with id: " + id);
        }

        irDocumentRepository.deleteById(id);
        log.info("IR document deleted successfully with id: {}", id);
    }
}