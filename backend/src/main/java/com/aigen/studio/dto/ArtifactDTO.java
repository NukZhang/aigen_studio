package com.aigen.studio.dto;

import com.aigen.studio.entity.Artifact;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.repository.GenerationJobRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

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
    // 作业的 GitLab 信息
    private Long gitlabGroupId;

    public static ArtifactDTO fromEntity(Artifact artifact) {
        ArtifactDTO dto = new ArtifactDTO();
        dto.setId(artifact.getId());
        dto.setJobId(artifact.getJobId());
        dto.setName(artifact.getName());
        dto.setType(artifact.getType());
        dto.setPath(artifact.getPath());
        dto.setPreview(artifact.getPreview());
        dto.setFileSize(artifact.getFileSize());
        dto.setGitlabProjectId(artifact.getGitlabProjectId());
        dto.setGitlabProjectUrl(artifact.getGitlabProjectUrl());
        dto.setGitlabBranch(artifact.getGitlabBranch());
        dto.setGitlabCommitId(artifact.getGitlabCommitId());
        dto.setGitlabFilePath(artifact.getGitlabFilePath());
        dto.setCreatedAt(artifact.getCreatedAt());

        return dto;
    }

    public static ArtifactDTO fromEntity(Artifact artifact, GenerationJob job) {
        ArtifactDTO dto = fromEntity(artifact);

        // 获取作业的 GitLab 信息
        if (job != null) {
            dto.setGitlabGroupId(job.getGitlabGroupId());
        }

        return dto;
    }
}