package com.aigen.studio.service;

import com.aigen.studio.dto.CreateRequirementRequest;
import com.aigen.studio.dto.RequirementDTO;
import com.aigen.studio.dto.UpdateRequirementRequest;
import com.aigen.studio.entity.Requirement;
import com.aigen.studio.repository.RequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequirementService {

    private final RequirementRepository requirementRepository;

    @Transactional
    public RequirementDTO createRequirement(CreateRequirementRequest request, String createdBy) {
        log.info("Creating requirement with code: {}", request.getCode());

        if (requirementRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Requirement code already exists: " + request.getCode());
        }

        Requirement requirement = new Requirement();
        requirement.setCode(request.getCode());
        requirement.setTitle(request.getTitle());
        requirement.setDescription(request.getDescription());
        requirement.setStatus(Requirement.RequirementStatus.DRAFT);
        requirement.setCreatedBy(createdBy);
        requirement.setUpdatedBy(createdBy);

        Requirement saved = requirementRepository.save(requirement);
        log.info("Requirement created successfully with id: {}", saved.getId());

        return RequirementDTO.fromEntity(saved);
    }

    @Transactional
    public RequirementDTO updateRequirement(Long id, UpdateRequirementRequest request, String updatedBy) {
        log.info("Updating requirement with id: {}", id);

        Requirement requirement = requirementRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Requirement not found with id: " + id));

        requirement.setTitle(request.getTitle());
        requirement.setDescription(request.getDescription());
        requirement.setUpdatedBy(updatedBy);

        Requirement saved = requirementRepository.save(requirement);
        log.info("Requirement updated successfully with id: {}", saved.getId());

        return RequirementDTO.fromEntity(saved);
    }

    @Transactional
    public RequirementDTO updateStatus(Long id, Requirement.RequirementStatus status, String updatedBy) {
        log.info("Updating requirement status to {} for id: {}", status, id);

        Requirement requirement = requirementRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Requirement not found with id: " + id));

        requirement.setStatus(status);
        requirement.setUpdatedBy(updatedBy);

        Requirement saved = requirementRepository.save(requirement);
        log.info("Requirement status updated successfully with id: {}", saved.getId());

        return RequirementDTO.fromEntity(saved);
    }

    public Optional<RequirementDTO> getRequirementById(Long id) {
        return requirementRepository.findById(id)
            .map(RequirementDTO::fromEntity);
    }

    public Optional<RequirementDTO> getRequirementByCode(String code) {
        return requirementRepository.findByCode(code)
            .map(RequirementDTO::fromEntity);
    }

    public List<RequirementDTO> getAllRequirements() {
        return requirementRepository.findAll().stream()
            .map(RequirementDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public List<RequirementDTO> getRequirementsByStatus(Requirement.RequirementStatus status) {
        return requirementRepository.findByStatus(status).stream()
            .map(RequirementDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRequirement(Long id) {
        log.info("Deleting requirement with id: {}", id);

        if (!requirementRepository.existsById(id)) {
            throw new RuntimeException("Requirement not found with id: " + id);
        }

        requirementRepository.deleteById(id);
        log.info("Requirement deleted successfully with id: {}", id);
    }
}