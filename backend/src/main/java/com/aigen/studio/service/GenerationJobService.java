package com.aigen.studio.service;

import com.aigen.studio.dto.GenerationJobDTO;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.entity.IRDocument;
import com.aigen.studio.repository.GenerationJobRepository;
import com.aigen.studio.repository.IRDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationJobService {

    private final GenerationJobRepository jobRepository;
    private final IRDocumentRepository irDocumentRepository;
    private final IFlowGenerationService iflowGenerationService;

    @Transactional
    public GenerationJobDTO createJob(Long requirementId, Long irDocumentId, String createdBy) {
        log.info("Creating generation job for requirement id: {}", requirementId);

        IRDocument irDocument = irDocumentRepository.findById(irDocumentId)
            .orElseThrow(() -> new RuntimeException("IR document not found with id: " + irDocumentId));

        if (irDocument.getStatus() != IRDocument.IRStatus.VALID) {
            throw new RuntimeException("IR document must be in VALID status before generating code");
        }

        String jobCode = "JOB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        GenerationJob job = new GenerationJob();
        job.setRequirementId(requirementId);
        job.setIrDocumentId(irDocumentId);
        job.setJobCode(jobCode);
        job.setStatus(GenerationJob.JobStatus.PENDING);
        job.setCreatedBy(createdBy);

        GenerationJob saved = jobRepository.save(job);
        log.info("Generation job created successfully with code: {}", jobCode);

        return GenerationJobDTO.fromEntity(saved);
    }

    @Async("taskExecutor")
    @Transactional
    public void executeJob(Long jobId) {
        log.info("Starting execution of job id: {}", jobId);

        GenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        try {
            job.setStatus(GenerationJob.JobStatus.RUNNING);
            job = jobRepository.save(job);

            iflowGenerationService.generateCode(job);

            job.setStatus(GenerationJob.JobStatus.SUCCESS);
            log.info("Job execution completed successfully for job id: {}", jobId);

        } catch (Exception e) {
            job.setStatus(GenerationJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            log.error("Job execution failed for job id: {}: {}", jobId, e.getMessage(), e);
        } finally {
            jobRepository.save(job);
        }
    }

    @Transactional
    public void updateJobLog(Long jobId, String logOutput) {
        log.debug("Updating log for job id: {}", jobId);

        GenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        String currentLog = job.getLogOutput() != null ? job.getLogOutput() : "";
        job.setLogOutput(currentLog + logOutput + "\n");
        jobRepository.save(job);
    }

    @Transactional
    public void updateJobGitInfo(Long jobId, String branch, String commitId, String pipelineId) {
        log.info("Updating Git info for job id: {}", jobId);

        GenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        job.setGitlabBranch(branch);
        job.setGitlabCommitId(commitId);
        job.setGitlabPipelineId(pipelineId);
        jobRepository.save(job);
    }

    public Optional<GenerationJobDTO> getJobById(Long id) {
        return jobRepository.findById(id)
            .map(GenerationJobDTO::fromEntity);
    }

    public Optional<GenerationJobDTO> getJobByCode(String jobCode) {
        return jobRepository.findByJobCode(jobCode)
            .map(GenerationJobDTO::fromEntity);
    }

    public List<GenerationJobDTO> getJobsByRequirementId(Long requirementId) {
        return jobRepository.findByRequirementId(requirementId).stream()
            .map(GenerationJobDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public List<GenerationJobDTO> getAllJobs() {
        return jobRepository.findAll().stream()
            .map(GenerationJobDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public List<GenerationJobDTO> getJobsByStatus(GenerationJob.JobStatus status) {
        return jobRepository.findByStatus(status).stream()
            .map(GenerationJobDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public String getDeliveryLogs(Long jobId) {
        log.info("Getting delivery logs for job id: {}", jobId);

        GenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        // 构建交付日志
        StringBuilder logs = new StringBuilder();
        logs.append("=== 交付日志 ===\n");
        logs.append(String.format("作业代码: %s\n", job.getJobCode()));
        logs.append(String.format("状态: %s\n", job.getStatus()));
        logs.append(String.format("创建时间: %s\n", job.getCreatedAt()));
        logs.append("\n=== GitLab 信息 ===\n");

        if (job.getGitlabGroupId() != null) {
            logs.append(String.format("GitLab 群组 ID: %d\n", job.getGitlabGroupId()));
        }

        if (job.getGitlabBranch() != null) {
            logs.append(String.format("分支: %s\n", job.getGitlabBranch()));
        }

        if (job.getGitlabCommitId() != null) {
            logs.append(String.format("提交 ID: %s\n", job.getGitlabCommitId()));
        }

        if (job.getGitlabPipelineId() != null) {
            logs.append(String.format("Pipeline ID: %s\n", job.getGitlabPipelineId()));
        }

        if (job.getGitlabPipelineStatus() != null) {
            logs.append(String.format("Pipeline 状态: %s\n", job.getGitlabPipelineStatus()));
        }

        if (job.getGitlabPipelineUrl() != null) {
            logs.append(String.format("Pipeline URL: %s\n", job.getGitlabPipelineUrl()));
        }

        if (job.getLogOutput() != null && !job.getLogOutput().isEmpty()) {
            logs.append("\n=== 执行日志 ===\n");
            logs.append(job.getLogOutput());
        }

        if (job.getErrorMessage() != null) {
            logs.append("\n=== 错误信息 ===\n");
            logs.append(job.getErrorMessage());
        }

        return logs.toString();
    }
}
