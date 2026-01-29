package com.aigen.studio.service;

import cn.iflow.sdk.core.IFlowClient;
import cn.iflow.sdk.types.config.IFlowOptions;
import cn.iflow.sdk.types.enums.ApprovalMode;
import cn.iflow.sdk.types.enums.PermissionMode;
import cn.iflow.sdk.types.messages.*;
import com.aigen.studio.entity.Artifact;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.repository.GenerationJobRepository;
import com.aigen.studio.repository.IRDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IFlowGenerationService {

    private final IRDocumentRepository irDocumentRepository;
    private final GenerationJobRepository jobRepository;
    private final ArtifactService artifactService;
    private final GitLabService gitLabService;

    @Value("${iflow.sdk.api-key}")
    private String iflowApiKey;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

    @Value("${iflow.sdk.timeout:300000}")
    private long timeoutMillis;

    public void generateCode(GenerationJob job) {
        log.info("Starting code generation for job: {}", job.getJobCode());

        var irDocument = irDocumentRepository.findById(job.getIrDocumentId())
            .orElseThrow(() -> new RuntimeException("IR document not found"));

        try {
            // 创建输出目录
            Path outputPath = Paths.get(outputDir, "job-" + job.getId());
            Files.createDirectories(outputPath);

            appendLog(job, "Initializing iFlow SDK...");
            appendLog(job, "Output directory: " + outputPath.toAbsolutePath());

            // 配置 iFlow 选项
            IFlowOptions options = IFlowOptions.builder()
                .autoStartProcess(true)
                .timeout(Duration.ofMillis(timeoutMillis))
                .permissionMode(PermissionMode.AUTO)
                .approvalMode(ApprovalMode.YOLO)
                .fileAccess(true)
                .fileReadOnly(false)
                .fileAllowedDirs(List.of(outputPath.toAbsolutePath().toString()))
                .cwd(outputPath.toAbsolutePath().toString())
                .build();

            // 创建 iFlow 客户端
            try (IFlowClient client = IFlowClient.create(options)) {
                appendLog(job, "Connecting to iFlow...");
                client.connect().block();

                appendLog(job, "Reading IR document content...");
                String irContent = irDocument.getContent();
                appendLog(job, "IR Content: " + irContent.substring(0, Math.min(200, irContent.length())) + "...");

                // 构建代码生成任务提示词
                String taskPrompt = buildTaskPrompt(irContent, outputPath);
                appendLog(job, "Sending code generation task to iFlow...");

                // 发送任务并接收响应
                client.sendMessage(taskPrompt).block();

                List<String> generatedFiles = new ArrayList<>();
                AtomicBoolean taskFinished = new AtomicBoolean(false);

                // 接收消息流
                client.receiveMessages()
                    .doOnNext(message -> {
                        if (message instanceof AssistantMessage) {
                            AssistantMessage assistantMsg = (AssistantMessage) message;
                            String text = assistantMsg.getChunk().getText();
                            appendLog(job, "Assistant: " + text);
                        } else if (message instanceof ToolCallMessage) {
                            ToolCallMessage toolCall = (ToolCallMessage) message;
                            appendLog(job, "Tool: " + toolCall.getLabel() + " - " + toolCall.getStatus());
                        } else if (message instanceof ToolResultMessage) {
                            ToolResultMessage result = (ToolResultMessage) message;
                            appendLog(job, "Tool Result: " + result.getContent());
                        } else if (message instanceof TaskFinishMessage) {
                            TaskFinishMessage finishMsg = (TaskFinishMessage) message;
                            appendLog(job, "Task finished: " + finishMsg.getStopReason());
                            taskFinished.set(true);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Error during code generation", error);
                        appendLog(job, "ERROR: " + error.getMessage());
                    })
                    .doOnComplete(() -> {
                        if (!taskFinished.get()) {
                            appendLog(job, "Code generation stream completed (no task finish message)");
                        }
                        // 扫描生成的文件
                        scanGeneratedFiles(outputPath, generatedFiles);
                        appendLog(job, "Generated " + generatedFiles.size() + " files");
                    })
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                        log.warn("Code generation timed out after {} ms", timeoutMillis);
                        appendLog(job, "Code generation timed out - scanning generated files...");
                        scanGeneratedFiles(outputPath, generatedFiles);
                        appendLog(job, "Generated " + generatedFiles.size() + " files (timed out)");
                        return Flux.empty();
                    })
                    .blockLast(); // 等待流完成

                appendLog(job, "Code generation completed successfully!");

                // 保存生成的文件到数据库
                if (!generatedFiles.isEmpty()) {
                    saveArtifacts(job, outputPath, generatedFiles);
                    appendLog(job, "Saved " + generatedFiles.size() + " artifacts to database");

                // 集成 GitLab：上传代码到仓库
                integrateWithGitLab(job, outputPath, generatedFiles);

                } else {
                    appendLog(job, "WARNING: No files were generated");
                    throw new RuntimeException("No files were generated during code generation");
                }

            } catch (Exception e) {
                log.error("iFlow client error", e);
                appendLog(job, "iFlow Client Error: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            log.error("Code generation failed for job: {}", job.getJobCode(), e);
            appendLog(job, "ERROR: " + e.getMessage());
            throw new RuntimeException("Code generation failed", e);
        }
    }

    private void saveArtifacts(GenerationJob job, Path outputPath, List<String> generatedFiles) {
        try {
            // 保存项目级别的产出物
            artifactService.createArtifact(
                job.getId(),
                "generated-project",
                Artifact.ArtifactType.PROJECT.name(),
                outputPath.toAbsolutePath().toString(),
                "Generated project for job " + job.getJobCode(),
                null
            );

            // 保存关键文件
            for (String filePath : generatedFiles) {
                Path file = Paths.get(filePath);
                String relativePath = outputPath.relativize(file).toString();
                
                // 判断文件类型
                if (relativePath.contains("openapi.yaml")) {
                    artifactService.createArtifact(
                        job.getId(),
                        relativePath,
                        Artifact.ArtifactType.OPENAPI_SPEC.name(),
                        filePath,
                        "OpenAPI specification",
                        Files.size(file)
                    );
                } else if (relativePath.startsWith("frontend/")) {
                    artifactService.createArtifact(
                        job.getId(),
                        relativePath,
                        Artifact.ArtifactType.FRONTEND_CODE.name(),
                        filePath,
                        "Frontend code",
                        Files.size(file)
                    );
                } else if (relativePath.startsWith("backend/")) {
                    artifactService.createArtifact(
                        job.getId(),
                        relativePath,
                        Artifact.ArtifactType.BACKEND_CODE.name(),
                        filePath,
                        "Backend code",
                        Files.size(file)
                    );
                }
            }
            
            log.info("Saved {} artifacts for job: {}", generatedFiles.size(), job.getJobCode());
        } catch (Exception e) {
            log.error("Failed to save artifacts", e);
            appendLog(job, "WARNING: Failed to save some artifacts: " + e.getMessage());
        }
    }

    private String buildTaskPrompt(String irContent, Path outputPath) {
        return String.format("""
            请根据以下 IR 配置生成完整的代码项目：

            IR 配置：
            %s

            要求：
            1. 在当前工作目录生成完整的项目结构
            2. 生成前端 Vue 3 项目（如果包含 frontend 模块）
            3. 生成后端 Spring Boot 项目（如果包含 backend 模块）
            4. 生成 OpenAPI 规范文件 openapi.yaml
            5. 生成 TypeScript SDK（如果需要）
            6. 确保所有代码都是完整的、可运行的
            7. 添加必要的配置文件和说明文档

            输出目录：%s

            请开始生成代码，并详细说明每个步骤。
            """, irContent, outputPath.toAbsolutePath());
    }

    private void scanGeneratedFiles(Path rootPath, List<String> generatedFiles) {
        try {
            Files.walk(rootPath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    generatedFiles.add(file.toAbsolutePath().toString());
                });
        } catch (Exception e) {
            log.error("Error scanning generated files", e);
        }
    }

    private void appendLog(GenerationJob job, String message) {
        String timestamp = java.time.LocalDateTime.now().toString();
        String logEntry = String.format("[%s] %s", timestamp, message);
        log.info(logEntry);

        String currentLog = job.getLogOutput() != null ? job.getLogOutput() : "";
        job.setLogOutput(currentLog + logEntry + "\n");

        // 实时保存日志到数据库
        try {
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Failed to save job log", e);
        }
    }

    /**
     * 集成 GitLab：上传代码到仓库并触发 Pipeline
     */
    private void integrateWithGitLab(GenerationJob job, Path outputPath, List<String> generatedFiles) {
        try {
            appendLog(job, "Starting GitLab integration...");

            // 获取需求信息以生成群组名称
            var irDocument = irDocumentRepository.findById(job.getIrDocumentId())
                .orElseThrow(() -> new RuntimeException("IR document not found"));

            // 生成群组名称：REQ-{requirementId}-JOB-{jobId}
            String groupName = String.format("REQ-%d-JOB-%d", job.getRequirementId(), job.getId());
            String groupDescription = String.format("Code generation for Requirement %d, Job %s", job.getRequirementId(), job.getJobCode());

            // 创建或获取群组
            appendLog(job, "Creating GitLab group: " + groupName);
            var group = gitLabService.createOrGetGroup(groupName, groupDescription);
            job.setGitlabGroupId(((Number) group.get("id")).longValue());
            jobRepository.save(job);

            // 检查是否有前端代码
            boolean hasFrontend = generatedFiles.stream().anyMatch(f -> f.contains("frontend"));
            boolean hasBackend = generatedFiles.stream().anyMatch(f -> f.contains("backend"));

            // 如果有前端代码，创建前端项目并推送
            if (hasFrontend) {
                appendLog(job, "Processing frontend project...");
                String frontendProjectName = groupName + "-frontend";
                String frontendProjectDesc = "Frontend project for " + job.getJobCode();

                var frontendProject = gitLabService.createOrGetProject(job.getGitlabGroupId(), frontendProjectName, frontendProjectDesc);
                job.setGitlabFrontendProjectId(((Number) frontendProject.get("id")).longValue());
                jobRepository.save(job);

                // 推送前端代码
                Path frontendPath = outputPath.resolve("frontend");
                if (Files.exists(frontendPath)) {
                    String branchName = "feature/" + job.getJobCode().toLowerCase();
                    appendLog(job, "Pushing frontend code to branch: " + branchName);
                    gitLabService.pushCode(job.getGitlabFrontendProjectId(), branchName, frontendPath.toFile(), "Initial commit: " + job.getJobCode());
                    job.setGitlabBranch(branchName);

                    // 更新前端 Artifact 的 Git 信息
                    appendLog(job, "Updating frontend artifacts with git info...");
                    artifactService.updateArtifactsGitInfoByType(
                        job.getId(),
                        "FRONTEND_CODE",
                        job.getGitlabFrontendProjectId(),
                        (String) frontendProject.get("web_url"),
                        branchName,
                        null  // commitId 可以在需要时添加
                    );

                    // 触发前端 Pipeline
                    appendLog(job, "Triggering frontend pipeline...");
                    var frontendPipeline = gitLabService.triggerPipeline(job.getGitlabFrontendProjectId(), branchName);
                    job.setGitlabPipelineId(frontendPipeline.get("id").toString());
                    job.setGitlabPipelineUrl((String) frontendProject.get("web_url") + "/-/pipelines/" + frontendPipeline.get("id"));
                    jobRepository.save(job);

                    // 等待前端 Pipeline 完成
                    appendLog(job, "Waiting for frontend pipeline to complete...");
                    boolean frontendSuccess = gitLabService.waitForPipeline(job.getGitlabFrontendProjectId(), ((Number) frontendPipeline.get("id")).longValue());
                    job.setGitlabPipelineStatus(frontendSuccess ? "SUCCESS" : "FAILED");
                    jobRepository.save(job);

                    if (frontendSuccess) {
                        appendLog(job, "Frontend pipeline completed successfully!");
                    } else {
                        appendLog(job, "WARNING: Frontend pipeline failed or timed out");
                    }
                }
            }

            // 如果有后端代码，创建后端项目并推送
            if (hasBackend) {
                appendLog(job, "Processing backend project...");
                String backendProjectName = groupName + "-backend";
                String backendProjectDesc = "Backend project for " + job.getJobCode();

                var backendProject = gitLabService.createOrGetProject(job.getGitlabGroupId(), backendProjectName, backendProjectDesc);
                job.setGitlabBackendProjectId(((Number) backendProject.get("id")).longValue());
                jobRepository.save(job);

                // 推送后端代码
                Path backendPath = outputPath.resolve("backend");
                if (Files.exists(backendPath)) {
                    String branchName = "feature/" + job.getJobCode().toLowerCase();
                    appendLog(job, "Pushing backend code to branch: " + branchName);
                    gitLabService.pushCode(job.getGitlabBackendProjectId(), branchName, backendPath.toFile(), "Initial commit: " + job.getJobCode());

                    // 更新后端 Artifact 的 Git 信息
                    appendLog(job, "Updating backend artifacts with git info...");
                    artifactService.updateArtifactsGitInfoByType(
                        job.getId(),
                        "BACKEND_CODE",
                        job.getGitlabBackendProjectId(),
                        (String) backendProject.get("web_url"),
                        branchName,
                        null  // commitId 可以在需要时添加
                    );

                    // 触发后端 Pipeline
                    appendLog(job, "Triggering backend pipeline...");
                    var backendPipeline = gitLabService.triggerPipeline(job.getGitlabBackendProjectId(), branchName);

                    // 等待后端 Pipeline 完成
                    appendLog(job, "Waiting for backend pipeline to complete...");
                    boolean backendSuccess = gitLabService.waitForPipeline(job.getGitlabBackendProjectId(), ((Number) backendPipeline.get("id")).longValue());

                    if (backendSuccess) {
                        appendLog(job, "Backend pipeline completed successfully!");
                    } else {
                        appendLog(job, "WARNING: Backend pipeline failed or timed out");
                    }
                }
            }

            appendLog(job, "GitLab integration completed successfully!");
            appendLog(job, "Group ID: " + job.getGitlabGroupId());
            appendLog(job, "Frontend Project ID: " + job.getGitlabFrontendProjectId());
            appendLog(job, "Backend Project ID: " + job.getGitlabBackendProjectId());
            appendLog(job, "Pipeline Status: " + job.getGitlabPipelineStatus());

        } catch (Exception e) {
            log.error("GitLab integration failed for job: {}", job.getJobCode(), e);
            appendLog(job, "ERROR: GitLab integration failed - " + e.getMessage());
            // 不抛出异常，因为代码生成已经成功，只是 GitLab 集成失败
        }
    }
}
