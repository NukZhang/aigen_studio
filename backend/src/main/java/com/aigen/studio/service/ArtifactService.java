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
        return artifactRepository.findByJobId(jobId).stream()
            .map(ArtifactDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public Optional<ArtifactDTO> getArtifactById(Long id) {
        return artifactRepository.findById(id)
            .map(ArtifactDTO::fromEntity);
    }

    public List<ArtifactDTO> getArtifactsByType(Long jobId, Artifact.ArtifactType type) {
        return artifactRepository.findByJobIdAndType(jobId, type).stream()
            .map(ArtifactDTO::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteArtifactsByJobId(Long jobId) {
        log.info("Deleting all artifacts for job id: {}", jobId);
        artifactRepository.deleteByJobId(jobId);
    }

    public List<ArtifactDTO> getAllArtifacts() {
        return artifactRepository.findAll().stream()
            .map(ArtifactDTO::fromEntity)
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
            jobId, Artifact.ArtifactType.valueOf(type));

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

        // 检查文件是否存在
        File file = new File(artifact.getPath());
        if (!file.exists()) {
            throw new RuntimeException("文件不存在: " + artifact.getPath());
        }

        // 检查作业信息
        GenerationJob job = jobRepository.findById(artifact.getJobId())
            .orElseThrow(() -> new RuntimeException("Job not found: " + artifact.getJobId()));

        try {
            // 步骤1: 检查或创建群组
            String groupName = String.format("REQ-%d-JOB-%d", job.getRequirementId(), job.getId());
            String groupDescription = String.format("Code generation for Requirement %d, Job %s", job.getRequirementId(), job.getJobCode());

            Map<String, Object> group;
            if (job.getGitlabGroupId() == null) {
                group = gitLabService.createOrGetGroup(groupName, groupDescription);
                job.setGitlabGroupId(((Number) group.get("id")).longValue());
                jobRepository.save(job);
                log.info("Created GitLab group: {}", groupName);
            } else {
                // 验证群组是否存在
                group = gitLabService.findGroupById(job.getGitlabGroupId());
                if (group == null) {
                    throw new RuntimeException("GitLab group not found: " + job.getGitlabGroupId());
                }
            }

            // 步骤2: 确定项目类型和名称
            String projectType = "";
            if (artifact.getType().equals(Artifact.ArtifactType.FRONTEND_CODE.name())) {
                projectType = "frontend";
            } else if (artifact.getType().equals(Artifact.ArtifactType.BACKEND_CODE.name())) {
                projectType = "backend";
            } else {
                throw new RuntimeException("不支持的产出物类型: " + artifact.getType());
            }

            String projectName = groupName + "-" + projectType;
            String projectDesc = projectType + " project for " + job.getJobCode();

            // 步骤3: 检查或创建项目
            Map<String, Object> project;
            Long projectId = null;

            if (projectType.equals("frontend") && job.getGitlabFrontendProjectId() != null) {
                projectId = job.getGitlabFrontendProjectId();
            } else if (projectType.equals("backend") && job.getGitlabBackendProjectId() != null) {
                projectId = job.getGitlabBackendProjectId();
            }

            if (projectId != null) {
                // 验证项目是否存在
                project = gitLabService.findProjectById(projectId);
                if (project == null) {
                    throw new RuntimeException("GitLab project not found: " + projectId);
                }
            } else {
                // 创建新项目
                project = gitLabService.createOrGetProject(job.getGitlabGroupId(), projectName, projectDesc);
                projectId = ((Number) project.get("id")).longValue();

                if (projectType.equals("frontend")) {
                    job.setGitlabFrontendProjectId(projectId);
                } else if (projectType.equals("backend")) {
                    job.setGitlabBackendProjectId(projectId);
                }
                jobRepository.save(job);
                log.info("Created GitLab project: {}", projectName);
            }

            // 步骤4: 确定分支名
            String branchName = job.getGitlabBranch();
            if (branchName == null) {
                branchName = "feature/" + job.getJobCode().toLowerCase();
                job.setGitlabBranch(branchName);
                jobRepository.save(job);
            }

            // 步骤5: 确保分支存在（如果不存在则创建）
            ensureBranchExists(projectId, branchName);

            // 步骤6: 上传文件（幂等操作）
            uploadFileToGitLab(projectId, branchName, file, artifact);

            // 步骤7: 更新产出物的 Git 信息
            updateArtifactGitInfo(
                artifact.getId(),
                projectId,
                (String) project.get("web_url"),
                branchName,
                null,  // commitId 可以在需要时添加
                artifact.getName()  // 使用 name 作为仓库内的相对路径
            );

            log.info("Artifact {} delivered to GitLab successfully", artifactId);

        } catch (Exception e) {
            log.error("Failed to deliver artifact {} to GitLab", artifactId, e);
            throw new RuntimeException("交付失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确保分支存在
     */
    private void ensureBranchExists(Long projectId, String branchName) {
        try {
            String gitlabUrl = gitLabConfig.getUrl();
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches/" + URLEncoder.encode(branchName, "UTF-8");

            // 检查分支是否存在
            ResponseEntity<Map> response = gitLabConfig.callGitLabApiForMap(url, HttpMethod.GET, null);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Branch already exists: {}", branchName);
                return;
            }

            // 分支不存在，尝试创建
            log.info("Branch does not exist, creating: {}", branchName);

            // 先检查项目是否有 main 分支
            String mainBranchUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches/main";
            ResponseEntity<Map> mainResponse = gitLabConfig.callGitLabApiForMap(mainBranchUrl, HttpMethod.GET, null);

            String refBranch = "main";
            if (mainResponse.getStatusCode() != HttpStatus.OK) {
                // 尝试 master 分支
                String masterBranchUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches/master";
                ResponseEntity<Map> masterResponse = gitLabConfig.callGitLabApiForMap(masterBranchUrl, HttpMethod.GET, null);
                if (masterResponse.getStatusCode() == HttpStatus.OK) {
                    refBranch = "master";
                } else {
                    throw new RuntimeException("无法找到默认分支（main 或 master），请先创建初始提交");
                }
            }

            // 创建新分支
            String createBranchUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
            Map<String, Object> branchData = new HashMap<>();
            branchData.put("branch", branchName);
            branchData.put("ref", refBranch);

            ResponseEntity<Map> createResponse = gitLabConfig.callGitLabApiForMap(createBranchUrl, HttpMethod.POST, branchData);

            if (createResponse.getStatusCode() == HttpStatus.CREATED) {
                log.info("Branch created successfully: {}", branchName);
            } else {
                throw new RuntimeException("创建分支失败，状态码: " + createResponse.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to ensure branch exists: {}", branchName, e);
            throw new RuntimeException("确保分支存在失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传文件到 GitLab（幂等操作）
     */
    private void uploadFileToGitLab(Long projectId, String branchName, File file, Artifact artifact) {
        try {
            String content = Files.readString(file.toPath());

            // 使用 artifact 的 name 作为仓库内的相对路径（如 frontend/src/App.vue）
            String repositoryPath = artifact.getName();
            log.info("Uploading file to GitLab - projectId: {}, branch: {}, path: {}", projectId, branchName, repositoryPath);

            // 构建 GitLab API URL - 需要对路径进行两次编码
            // 第一次：对文件路径进行编码（替换 /）
            String encodedPath = repositoryPath.replace("/", "%2F");
            String gitlabUrl = gitLabConfig.getUrl();
            String fileUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/" + encodedPath;

            log.debug("GitLab API URL: {}", fileUrl);

            // 准备请求数据
            Map<String, Object> fileData = new HashMap<>();
            fileData.put("file_path", repositoryPath);
            fileData.put("branch", branchName);
            fileData.put("content", content);
            fileData.put("commit_message", "Deliver artifact: " + artifact.getName());

            // 调用 GitLab API 上传文件
            ResponseEntity<Map> response = gitLabConfig.callGitLabApiForMap(fileUrl, HttpMethod.POST, fileData);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("File uploaded successfully: {}", repositoryPath);
            } else {
                log.error("Upload failed with status: {}, body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("上传失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to upload file to GitLab", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
}