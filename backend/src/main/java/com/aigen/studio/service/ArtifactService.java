package com.aigen.studio.service;

import com.aigen.studio.config.GitLabProperties;
import com.aigen.studio.dto.ArtifactDTO;
import com.aigen.studio.entity.Artifact;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.repository.ArtifactRepository;
import com.aigen.studio.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final GitLabProperties gitLabConfig;
    private final GenerationJobRepository jobRepository;
    private final GitLabService gitLabService;

    @Transactional
    public ArtifactDTO createArtifact(Long jobId, String name, String type, String path, String preview, Long fileSize) {
        log.info("Creating artifact: {} for job id: {}", name, jobId);

        Artifact artifact = new Artifact();
        artifact.setJobId(jobId);
        artifact.setName(name);
        artifact.setType(type);
        artifact.setPath(path);
        artifact.setPreview(preview);
        artifact.setFileSize(fileSize);

        Artifact saved = artifactRepository.save(artifact);
        log.info("Artifact created successfully with id: {}", saved.getId());

        return ArtifactDTO.fromEntity(saved);
    }

    @Transactional
    public ArtifactDTO createArtifactWithGitInfo(
            Long jobId, String name, String type, String path, String preview, Long fileSize,
            Long gitlabProjectId, String gitlabProjectUrl, String gitlabBranch,
            String gitlabCommitId, String gitlabFilePath) {
        log.info("Creating artifact with git info: {} for job id: {}", name, jobId);

        Artifact artifact = new Artifact();
        artifact.setJobId(jobId);
        artifact.setName(name);
        artifact.setType(type);
        artifact.setPath(path);
        artifact.setPreview(preview);
        artifact.setFileSize(fileSize);
        artifact.setGitlabProjectId(gitlabProjectId);
        artifact.setGitlabProjectUrl(gitlabProjectUrl);
        artifact.setGitlabBranch(gitlabBranch);
        artifact.setGitlabCommitId(gitlabCommitId);
        artifact.setGitlabFilePath(gitlabFilePath);

        Artifact saved = artifactRepository.save(artifact);
        log.info("Artifact with git info created successfully with id: {}", saved.getId());

        return ArtifactDTO.fromEntity(saved);
    }

    public List<ArtifactDTO> getArtifactsByJobId(Long jobId) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        return artifactRepository.findByJobId(jobId).stream()
            .map(artifact -> ArtifactDTO.fromEntity(artifact, job))
            .collect(Collectors.toList());
    }

    public Optional<ArtifactDTO> getArtifactById(Long id) {
        return artifactRepository.findById(id)
            .map(artifact -> {
                GenerationJob job = jobRepository.findById(artifact.getJobId()).orElse(null);
                return ArtifactDTO.fromEntity(artifact, job);
            });
    }

    public List<ArtifactDTO> getArtifactsByType(Long jobId, String type) {
        GenerationJob job = jobRepository.findById(jobId).orElse(null);
        return artifactRepository.findByJobIdAndType(jobId, type).stream()
            .map(artifact -> ArtifactDTO.fromEntity(artifact, job))
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteArtifactsByJobId(Long jobId) {
        log.info("Deleting all artifacts for job id: {}", jobId);
        artifactRepository.deleteByJobId(jobId);
    }

    public List<ArtifactDTO> getAllArtifacts() {
        return artifactRepository.findAll().stream()
            .map(artifact -> {
                GenerationJob job = jobRepository.findById(artifact.getJobId()).orElse(null);
                return ArtifactDTO.fromEntity(artifact, job);
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public void updateArtifactGitInfo(
            Long artifactId,
            Long gitlabProjectId,
            String gitlabProjectUrl,
            String gitlabBranch,
            String gitlabCommitId,
            String gitlabFilePath) {
        log.info("Updating git info for artifact id: {}", artifactId);

        Artifact artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new RuntimeException("Artifact not found with id: " + artifactId));

        artifact.setGitlabProjectId(gitlabProjectId);
        artifact.setGitlabProjectUrl(gitlabProjectUrl);
        artifact.setGitlabBranch(gitlabBranch);
        artifact.setGitlabCommitId(gitlabCommitId);
        artifact.setGitlabFilePath(gitlabFilePath);

        artifactRepository.save(artifact);
    }

    @Transactional
    public void updateArtifactsGitInfoByType(
            Long jobId,
            String type,
            Long gitlabProjectId,
            String gitlabProjectUrl,
            String gitlabBranch,
            String gitlabCommitId) {
        List<Artifact> artifacts = artifactRepository.findByJobIdAndType(
            jobId, type);

        for (Artifact artifact : artifacts) {
            String gitlabFilePath = artifact.getPath();
            updateArtifactGitInfo(
                artifact.getId(),
                gitlabProjectId,
                gitlabProjectUrl,
                gitlabBranch,
                gitlabCommitId,
                gitlabFilePath
            );
        }
    }

    @Transactional
    public void deliverArtifactToGitLab(Long artifactId) {
        log.info("Delivering artifact {} to GitLab", artifactId);

        Artifact artifact = artifactRepository.findById(artifactId)
            .orElseThrow(() -> new RuntimeException("Artifact not found: " + artifactId));

        // 检查作业信息
        GenerationJob job = jobRepository.findById(artifact.getJobId())
            .orElseThrow(() -> new RuntimeException("Job not found: " + artifact.getJobId()));

        try {
            // 步骤1: 获取AIGen group
            String groupName = "AIGen";
            Map<String, Object> aigenGroup = gitLabService.findGroupByName(groupName);
            if (aigenGroup == null) {
                throw new RuntimeException("AIGen group not found. Please create the AIGen group in GitLab first.");
            }
            Long groupId = ((Number) aigenGroup.get("id")).longValue();
            
            // 步骤2: 确定分支名
            String branchName = "v.1.0.0";
            
            // 步骤3: 检查项目路径
            String projectPath = artifact.getPath();
            if (projectPath == null || projectPath.isEmpty()) {
                throw new RuntimeException("Artifact path is empty");
            }
            
            File projectDir = new File(projectPath);
            if (!projectDir.exists()) {
                throw new RuntimeException("Project directory does not exist: " + projectPath);
            }
            
            // 步骤4: 创建或获取GitLab项目
            String projectName = "job-" + job.getId();
            String projectDesc = "Generated code for Job " + job.getJobCode();
            Map<String, Object> project = gitLabService.createOrGetProject(groupId, projectName, projectDesc);
            Long projectId = ((Number) project.get("id")).longValue();
            
            // 步骤5: 检查并创建v.1.0.0分支
            ensureBranchExists(projectId, branchName);
            
            // 步骤6: 扫描并上传所有文件
            List<Artifact> allArtifacts = artifactRepository.findByJobId(job.getId());
            log.info("Found {} artifacts total", allArtifacts.size());
            
            // 过滤掉PROJECT类型的artifact，只上传实际文件
            List<Artifact> fileArtifacts = allArtifacts.stream()
                .filter(a -> !Artifact.ArtifactType.PROJECT.name().equals(a.getType()))
                .toList();
            log.info("Found {} file artifacts to upload", fileArtifacts.size());
            
            if (!fileArtifacts.isEmpty()) {
                uploadFilesUsingCommitApi(projectId, branchName, fileArtifacts);
            }
            
            // 步骤7: 更新artifact的Git信息
            updateArtifactGitInfo(
                artifactId,
                projectId,
                gitLabConfig.getUrl() + "/" + projectId,
                branchName,
                null,
                projectName
            );

            log.info("Artifact {} delivered to GitLab successfully", artifactId);

        } catch (Exception e) {
            log.error("Failed to deliver artifact {} to GitLab", artifactId, e);
            throw new RuntimeException("交付失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确保分支存在（简化版）
     */
    private void ensureBranchExists(Long projectId, String branchName) {
        try {
            String gitlabUrl = gitLabConfig.getUrl();
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches/" + URLEncoder.encode(branchName, "UTF-8");

            // 检查分支是否存在
            try {
                ResponseEntity<Map> response = gitLabConfig.callGitLabApiForMap(url, HttpMethod.GET, null);

                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Branch already exists: {}", branchName);
                    return;
                }
            } catch (RuntimeException e) {
                // 如果是404错误，说明分支不存在，继续创建
                if (e.getCause() instanceof org.springframework.web.client.HttpClientErrorException) {
                    org.springframework.web.client.HttpClientErrorException httpEx = 
                        (org.springframework.web.client.HttpClientErrorException) e.getCause();
                    if (httpEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.info("Branch does not exist, creating: {}", branchName);
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }

            // 检查项目是否有任何分支
            boolean hasBranches = gitLabService.checkProjectHasBranches(projectId);

            if (hasBranches) {
                // 有其他分支，创建新分支
                String createBranchUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
                Map<String, Object> branchData = new HashMap<>();
                branchData.put("branch", branchName);
                branchData.put("ref", "main");

                ResponseEntity<Map> createResponse = gitLabConfig.callGitLabApiForMap(createBranchUrl, HttpMethod.POST, branchData);

                if (createResponse.getStatusCode() == HttpStatus.CREATED) {
                    log.info("Branch created successfully: {}", branchName);
                } else {
                    throw new RuntimeException("创建分支失败，状态码: " + createResponse.getStatusCode());
                }
            } else {
                // 空项目，创建初始提交
                log.info("Creating initial commit on branch {} for new project {}", branchName, projectId);
                String fileUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/README.md";
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("file_path", "README.md");
                fileData.put("branch", branchName);
                fileData.put("content", "# " + branchName + "\n\nInitial commit");
                fileData.put("commit_message", "Initial commit");
                fileData.put("author_email", "aigen@example.com");
                fileData.put("author_name", "AIGen");

                ResponseEntity<Map> fileResponse = gitLabConfig.callGitLabApiForMap(fileUrl, HttpMethod.POST, fileData);

                if (fileResponse.getStatusCode() == HttpStatus.CREATED) {
                    log.info("Initial commit created on branch {}", branchName);
                } else {
                    throw new RuntimeException("创建初始提交失败，状态码: " + fileResponse.getStatusCode());
                }
            }

        } catch (Exception e) {
            log.error("Failed to ensure branch exists: {}", branchName, e);
            throw new RuntimeException("确保分支存在失败: " + e.getMessage(), e);
        }
    }

    

    /**
     * 使用Commit API批量上传文件
     */
    private void uploadFilesUsingCommitApi(Long projectId, String branchName, List<Artifact> artifacts) {
        try {
            log.info("Uploading {} files using Commit API to project {} on branch {}", artifacts.size(), projectId, branchName);

            String gitlabUrl = gitLabConfig.getUrl();
            String commitUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/commits";

            // 准备提交数据
            Map<String, Object> commitData = new HashMap<>();
            commitData.put("branch", branchName);
            commitData.put("commit_message", "Batch upload artifacts");
            commitData.put("author_email", "aigen@example.com");
            commitData.put("author_name", "AIGen");

            // 准备文件操作列表
            List<Map<String, Object>> actions = new java.util.ArrayList<>();

            for (Artifact artifact : artifacts) {
                File file = new File(artifact.getPath());
                if (!file.exists()) {
                    log.warn("File does not exist, skipping: {}", artifact.getPath());
                    continue;
                }

                String content = Files.readString(file.toPath());
                String repositoryPath = artifact.getName();

                Map<String, Object> action = new HashMap<>();
                action.put("action", "create");
                action.put("file_path", repositoryPath);
                action.put("content", content);

                actions.add(action);
                log.info("Added file to commit: {}", repositoryPath);
            }

            if (actions.isEmpty()) {
                log.warn("No files to upload");
                return;
            }

            commitData.put("actions", actions);

            // 调用 GitLab Commit API
            ResponseEntity<Map> response = gitLabConfig.callGitLabApiForMap(commitUrl, HttpMethod.POST, commitData);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Batch upload successful: {} files uploaded", actions.size());
            } else {
                log.error("Batch upload failed with status: {}, body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("批量上传失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to upload files using Commit API", e);
            throw new RuntimeException("批量上传失败: " + e.getMessage(), e);
        }
    }
}