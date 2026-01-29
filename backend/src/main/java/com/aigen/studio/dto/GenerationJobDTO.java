package com.aigen.studio.dto;

import com.aigen.studio.entity.GenerationJob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJobDTO {
    private Long id;
    private Long requirementId;
    private Long irDocumentId;
    private String jobCode;
    private GenerationJob.JobStatus status;
    private String logOutput;
    private String gitlabBranch;
    private String gitlabCommitId;
    private String gitlabPipelineId;
    private Long gitlabGroupId;
    private Long gitlabFrontendProjectId;
    private Long gitlabBackendProjectId;
    private String gitlabPipelineStatus;
    private String gitlabPipelineUrl;
    private String errorMessage;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public static GenerationJobDTO fromEntity(GenerationJob job) {
        return new GenerationJobDTO(
            job.getId(),
            job.getRequirementId(),
            job.getIrDocumentId(),
            job.getJobCode(),
            job.getStatus(),
            job.getLogOutput(),
            job.getGitlabBranch(),
            job.getGitlabCommitId(),
            job.getGitlabPipelineId(),
            job.getGitlabGroupId(),
            job.getGitlabFrontendProjectId(),
            job.getGitlabBackendProjectId(),
            job.getGitlabPipelineStatus(),
            job.getGitlabPipelineUrl(),
            job.getErrorMessage(),
            job.getCreatedBy(),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }
}