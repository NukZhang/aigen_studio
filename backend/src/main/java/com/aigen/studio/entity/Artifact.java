package com.aigen.studio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "artifacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(columnDefinition = "TEXT")
    private String preview;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "gitlab_project_id")
    private Long gitlabProjectId;

    @Column(name = "gitlab_project_url")
    private String gitlabProjectUrl;

    @Column(name = "gitlab_branch")
    private String gitlabBranch;

    @Column(name = "gitlab_commit_id")
    private String gitlabCommitId;

    @Column(name = "gitlab_file_path")
    private String gitlabFilePath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ArtifactType {
        FRONTEND_CODE, BACKEND_CODE, OPENAPI_SPEC, SDK, DOCUMENTATION, EVIDENCE, PROJECT
    }
}
