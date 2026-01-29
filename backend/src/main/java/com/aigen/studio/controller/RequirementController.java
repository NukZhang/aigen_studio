package com.aigen.studio.controller;

import com.aigen.studio.dto.*;
import com.aigen.studio.entity.Requirement;
import com.aigen.studio.service.RequirementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/requirements")
@RequiredArgsConstructor
@Slf4j
public class RequirementController {

    private final RequirementService requirementService;

    @PostMapping
    public ResponseEntity<RequirementDTO> createRequirement(
            @Valid @RequestBody CreateRequirementRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Creating requirement: {}", request.getCode());
        RequirementDTO created = requirementService.createRequirement(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RequirementDTO> updateRequirement(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequirementRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Updating requirement: {}", id);
        RequirementDTO updated = requirementService.updateRequirement(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RequirementDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam Requirement.RequirementStatus status,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        log.info("Updating status for requirement: {}", id);
        RequirementDTO updated = requirementService.updateStatus(id, status, userId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequirementDTO> getRequirement(@PathVariable Long id) {
        return requirementService.getRequirementById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<RequirementDTO> getRequirementByCode(@PathVariable String code) {
        return requirementService.getRequirementByCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<RequirementDTO>> getAllRequirements(
            @RequestParam(required = false) Requirement.RequirementStatus status) {
        if (status != null) {
            return ResponseEntity.ok(requirementService.getRequirementsByStatus(status));
        }
        return ResponseEntity.ok(requirementService.getAllRequirements());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRequirement(@PathVariable Long id) {
        log.info("Deleting requirement: {}", id);
        requirementService.deleteRequirement(id);
        return ResponseEntity.noContent().build();
    }
}