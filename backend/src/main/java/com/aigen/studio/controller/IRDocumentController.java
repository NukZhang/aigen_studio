package com.aigen.studio.controller;

import com.aigen.studio.dto.IRDocumentDTO;
import com.aigen.studio.service.IRDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ir-documents")
@RequiredArgsConstructor
@Slf4j
public class IRDocumentController {

    private final IRDocumentService irDocumentService;

    @PostMapping
    public ResponseEntity<IRDocumentDTO> createIRDocument(
            @RequestParam Long requirementId,
            @RequestBody String content,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Creating IR document for requirement: {}", requirementId);
        IRDocumentDTO created = irDocumentService.createIRDocument(requirementId, content, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IRDocumentDTO> updateIRDocument(
            @PathVariable Long id,
            @RequestBody String content,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Updating IR document: {}", id);
        IRDocumentDTO updated = irDocumentService.updateIRDocument(id, content, userId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<IRDocumentDTO> validateIRDocument(@PathVariable Long id) {
        log.info("Validating IR document: {}", id);
        IRDocumentDTO validated = irDocumentService.validateIRDocument(id);
        return ResponseEntity.ok(validated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IRDocumentDTO> getIRDocument(@PathVariable Long id) {
        return irDocumentService.getIRDocumentById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/requirement/{requirementId}")
    public ResponseEntity<IRDocumentDTO> getIRDocumentByRequirementId(@PathVariable Long requirementId) {
        return irDocumentService.getIRDocumentByRequirementId(requirementId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<IRDocumentDTO>> getAllIRDocuments() {
        return ResponseEntity.ok(irDocumentService.getAllIRDocuments());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIRDocument(@PathVariable Long id) {
        log.info("Deleting IR document: {}", id);
        irDocumentService.deleteIRDocument(id);
        return ResponseEntity.noContent().build();
    }
}