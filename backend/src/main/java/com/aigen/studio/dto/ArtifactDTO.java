package com.aigen.studio.dto;

import com.aigen.studio.entity.Artifact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactDTO {
    private Long id;
    private Long jobId;
    private String name;
    private String type;
    private String path;
    private String preview;
    private Long fileSize;
    private Long gitlabProjectId;
    private String gitlabProjectUrl;
    private String gitlabBranch;
    private String gitlabCommitId;
    private String gitlabFilePath;
    private java.time.LocalDateTime createdAt;

    public static ArtifactDTO fromEntity(Artifact artifact) {
        return new ArtifactDTO(
            artifact.getId(),
            artifact.getJobId(),
            artifact.getName(),
            artifact.getType(),
            artifact.getPath(),
            artifact.getPreview(),
            artifact.getFileSize(),
            artifact.getGitlabProjectId(),
            artifact.getGitlabProjectUrl(),
            artifact.getGitlabBranch(),
            artifact.getGitlabCommitId(),
            artifact.getGitlabFilePath(),
            artifact.getCreatedAt()
        );
    }
}