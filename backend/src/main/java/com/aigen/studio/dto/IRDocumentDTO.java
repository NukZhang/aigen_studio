package com.aigen.studio.dto;

import com.aigen.studio.entity.IRDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IRDocumentDTO {
    private Long id;
    private Long requirementId;
    private String content;
    private IRDocument.IRStatus status;
    private String validationErrors;
    private String createdBy;
    private String updatedBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public static IRDocumentDTO fromEntity(IRDocument irDocument) {
        return new IRDocumentDTO(
            irDocument.getId(),
            irDocument.getRequirementId(),
            irDocument.getContent(),
            irDocument.getStatus(),
            irDocument.getValidationErrors(),
            irDocument.getCreatedBy(),
            irDocument.getUpdatedBy(),
            irDocument.getCreatedAt(),
            irDocument.getUpdatedAt()
        );
    }
}