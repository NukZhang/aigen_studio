package com.aigen.studio.service;

import com.aigen.studio.entity.Artifact;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.repository.ArtifactRepository;
import com.aigen.studio.repository.GenerationJobRepository;
import com.aigen.studio.repository.IRDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IFlowGenerationService {

    private final IRDocumentRepository irDocumentRepository;
    private final GenerationJobRepository jobRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final GitLabService gitLabService;
    private final IFlowTaskService iFlowTaskService;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

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

            appendLog(job, "Reading IR document content...");
            String irContent = irDocument.getContent();
            appendLog(job, "IR Content: " + irContent.substring(0, Math.min(200, irContent.length())) + "...");

            appendLog(job, "Sending code generation task to iFlow...");

            List<String> generatedFiles = new ArrayList<>();

            // 使用 IFlowTaskService 生成代码
            iFlowTaskService.generateCode(irContent, outputPath, message -> {
                appendLog(job, message);
            });

            // 扫描生成的文件
            scanGeneratedFiles(outputPath, generatedFiles);
            appendLog(job, "Generated " + generatedFiles.size() + " files");

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
            log.error("Code generation failed for job: {}", job.getJobCode(), e);
            appendLog(job, "ERROR: " + e.getMessage());
            throw new RuntimeException("Code generation failed", e);
        }
    }

    private void saveArtifacts(GenerationJob job, Path outputPath, List<String> generatedFiles) {
        try {
            // 确保outputPath是绝对路径
            Path absoluteOutputPath = outputPath.toAbsolutePath();

            // 只保存整个项目作为一个产出物
            long totalSize = generatedFiles.stream()
                .mapToLong(filePath -> {
                    try {
                        return Files.size(Paths.get(filePath));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();

            artifactService.createArtifact(
                job.getId(),
                "generated-project",
                Artifact.ArtifactType.PROJECT.name(),
                absoluteOutputPath.toString(),
                "Generated project for job " + job.getJobCode() + " (" + generatedFiles.size() + " files)",
                totalSize
            );

            log.info("Saved 1 project artifact for job: {} ({} files, total size: {} bytes)",
                job.getJobCode(), generatedFiles.size(), totalSize);
        } catch (Exception e) {
            log.error("Failed to save artifacts", e);
            appendLog(job, "WARNING: Failed to save artifacts: " + e.getMessage());
        }
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

            // 获取 AIGen group
            appendLog(job, "Finding AIGen group...");
            String groupName = "AIGen";
            var aigenGroup = gitLabService.findGroupByName(groupName);
            if (aigenGroup == null) {
                appendLog(job, "ERROR: AIGen group not found. Please create the AIGen group in GitLab first.");
                return;
            }
            Long groupId = ((Number) aigenGroup.get("id")).longValue();
            job.setGitlabGroupId(groupId);
            jobRepository.save(job);

            // 创建或获取 job 项目
            appendLog(job, "Creating job project...");
            String projectName = "job-" + job.getId();
            String projectDesc = "Generated code for Job " + job.getJobCode();
            var project = gitLabService.createOrGetProject(groupId, projectName, projectDesc);
            Long projectId = ((Number) project.get("id")).longValue();

            // 确定分支名
            String branchName = "v.1.0.0";
            job.setGitlabBranch(branchName);
            jobRepository.save(job);

            // 上传所有文件
            appendLog(job, "Uploading files to GitLab...");
            gitLabService.pushCode(projectId, branchName, outputPath.toFile(), "Initial commit: " + job.getJobCode());

            // 更新所有 artifact 的 Git 信息
            appendLog(job, "Updating artifacts with git info...");
            List<Artifact> allArtifacts = artifactRepository.findByJobId(job.getId());
            for (Artifact artifact : allArtifacts) {
                artifact.setGitlabProjectId(projectId);
                artifact.setGitlabProjectUrl((String) project.get("web_url"));
                artifact.setGitlabBranch(branchName);
                artifactRepository.save(artifact);
            }

            appendLog(job, "GitLab integration completed successfully!");
            appendLog(job, "Group: " + groupName);
            appendLog(job, "Project: " + projectName);
            appendLog(job, "Branch: " + branchName);

        } catch (Exception e) {
            log.error("GitLab integration failed for job: {}", job.getJobCode(), e);
            appendLog(job, "ERROR: GitLab integration failed - " + e.getMessage());
            // 不抛出异常，因为代码生成已经成功，只是 GitLab 集成失败
        }
    }
}
