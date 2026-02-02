package com.aigen.studio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generation_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "ir_document_id", nullable = false)
    private Long irDocumentId;

    @Column(nullable = false, unique = true)
    private String jobCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(columnDefinition = "TEXT")
    private String logOutput;

    @Column(name = "gitlab_branch")
    private String gitlabBranch;

    @Column(name = "gitlab_commit_id")
    private String gitlabCommitId;

    @Column(name = "gitlab_pipeline_id")
    private String gitlabPipelineId;

    @Column(name = "gitlab_group_id")
    private Long gitlabGroupId;

    @Column(name = "gitlab_pipeline_status")
    private String gitlabPipelineStatus;

    @Column(name = "gitlab_pipeline_url")
    private String gitlabPipelineUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum JobStatus {
        PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    }
}