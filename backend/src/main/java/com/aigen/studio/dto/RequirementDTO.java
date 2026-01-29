package com.aigen.studio.dto;

import com.aigen.studio.entity.Requirement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementDTO {
    private Long id;
    private String code;
    private String title;
    private String description;
    private Requirement.RequirementStatus status;
    private String createdBy;
    private String updatedBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public static RequirementDTO fromEntity(Requirement requirement) {
        return new RequirementDTO(
            requirement.getId(),
            requirement.getCode(),
            requirement.getTitle(),
            requirement.getDescription(),
            requirement.getStatus(),
            requirement.getCreatedBy(),
            requirement.getUpdatedBy(),
            requirement.getCreatedAt(),
            requirement.getUpdatedAt()
        );
    }
}