package com.aigen.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabService {

    @Value("${gitlab.url}")
    private String gitlabUrl;

    @Value("${gitlab.token}")
    private String gitlabToken;

    @Value("${gitlab.base-path:AIGen}")
    private String basePath;

    @Value("${gitlab.timeout:600000}")
    private long timeoutMillis;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 创建或获取 GitLab 群组
     */
    public Map<String, Object> createOrGetGroup(String groupName, String description) {
        log.info("Creating or getting GitLab group: {}", groupName);

        // 查找群组
        String searchUrl = gitlabUrl + "/api/v4/groups?search=" + groupName;
        ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

        if (searchResponse.getStatusCode() == HttpStatus.OK && searchResponse.getBody() != null && !searchResponse.getBody().isEmpty()) {
            List<Map<String, Object>> groups = searchResponse.getBody();
            for (Map<String, Object> group : groups) {
                if (groupName.equals(group.get("name")) || groupName.equals(group.get("path"))) {
                    log.info("Group already exists: {}", groupName);
                    return group;
                }
            }
        }

        // 获取父级群组
        Map<String, Object> parentGroup = findOrCreateParentGroup();
        if (parentGroup == null || parentGroup.get("id") == null) {
            throw new RuntimeException("Failed to get or create parent group");
        }

        // 创建新群组
        String createUrl = gitlabUrl + "/api/v4/groups";
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("name", groupName);
        groupData.put("path", groupName);
        groupData.put("description", description);
        groupData.put("visibility", "private");
        groupData.put("parent_id", parentGroup.get("id"));

        ResponseEntity<Map> createResponse = callGitLabApiForMap(createUrl, HttpMethod.POST, groupData);

        if (createResponse.getStatusCode() == HttpStatus.CREATED && createResponse.getBody() != null) {
            Map<String, Object> createdGroup = createResponse.getBody();
            log.info("Group created successfully: {} (ID: {})", groupName, createdGroup.get("id"));
            return createdGroup;
        } else {
            log.error("Failed to create group. Status: {}, Body: {}", createResponse.getStatusCode(), createResponse.getBody());
            throw new RuntimeException("Failed to create group: " + groupName);
        }
    }

    /**
     * 创建或获取 GitLab 项目
     */
    public Map<String, Object> createOrGetProject(Long groupId, String projectName, String description) {
        log.info("Creating or getting GitLab project: {}", projectName);

        // 查找项目
        String searchUrl = gitlabUrl + "/api/v4/projects?search=" + projectName;
        ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

        if (searchResponse.getStatusCode() == HttpStatus.OK && !searchResponse.getBody().isEmpty()) {
            List<Map<String, Object>> projects = searchResponse.getBody();
            for (Map<String, Object> project : projects) {
                if (projectName.equals(project.get("name")) || projectName.equals(project.get("path"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> namespace = (Map<String, Object>) project.get("namespace");
                    if (namespace != null && groupId.equals(namespace.get("id"))) {
                        log.info("Project already exists: {}", projectName);
                        return project;
                    }
                }
            }
        }

        // 创建新项目
        String createUrl = gitlabUrl + "/api/v4/projects";
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("name", projectName);
        projectData.put("path", projectName);
        projectData.put("description", description);
        projectData.put("namespace_id", groupId);
        projectData.put("visibility", "private");
        projectData.put("issues_enabled", false);
        projectData.put("wiki_enabled", false);
        projectData.put("snippets_enabled", false);
        projectData.put("merge_requests_enabled", true);

        ResponseEntity<Map> createResponse = callGitLabApiForMap(createUrl, HttpMethod.POST, projectData);

        if (createResponse.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> createdProject = createResponse.getBody();
            log.info("Project created successfully: {} (ID: {})", projectName, createdProject.get("id"));
            return createdProject;
        } else {
            throw new RuntimeException("Failed to create project: " + projectName);
        }
    }

    /**
     * 推送代码到 GitLab 项目
     */
    public void pushCode(Long projectId, String branchName, File sourceDir, String commitMessage) {
        log.info("Pushing code to project ID: {}, branch: {}", projectId, branchName);

        try {
            // 检查项目是否有任何分支
            boolean hasBranches = checkProjectHasBranches(projectId);

            // 如果没有分支，这是新项目，需要创建初始提交
            if (!hasBranches) {
                log.info("Project has no branches, creating initial commit");
                createInitialCommit(projectId, branchName);
            } else {
                // 创建分支（如果不存在）
                createBranch(projectId, branchName, "main");
            }

            // 遍历源目录中的所有文件
            Path sourcePath = sourceDir.toPath();

            Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                        String content = Files.readString(file);

                        // 上传文件 - 需要对路径进行 URL 编码
                        String encodedPath = java.net.URLEncoder.encode(relativePath, "UTF-8");
                        String fileUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/" + encodedPath;
                        Map<String, Object> fileData = new HashMap<>();
                        fileData.put("file_path", relativePath);
                        fileData.put("branch", branchName);
                        fileData.put("content", content);
                        fileData.put("commit_message", commitMessage);

                        ResponseEntity<Map> response = callGitLabApiForMap(fileUrl, HttpMethod.POST, fileData);

                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            log.info("File pushed: {}", relativePath);
                        } else {
                            log.error("Failed to push file: {} (Status: {})", relativePath, response.getStatusCode());
                        }

                    } catch (Exception e) {
                        log.error("Failed to push file: {}", file, e);
                    }
                });

            log.info("All files pushed successfully to project ID: {}", projectId);

        } catch (Exception e) {
            log.error("Failed to push code to project ID: {}", projectId, e);
            throw new RuntimeException("Failed to push code", e);
        }
    }

    /**
     * 检查项目是否有任何分支
     */
    private boolean checkProjectHasBranches(Long projectId) {
        try {
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
            ResponseEntity<List> response = callGitLabApiForList(url, HttpMethod.GET, null);

            if (response.getStatusCode() == HttpStatus.OK) {
                List branches = response.getBody();
                return branches != null && !branches.isEmpty();
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check project branches", e);
            return false;
        }
    }

    /**
     * 创建初始提交（使用 README.md 作为第一个文件）
     */
    private void createInitialCommit(Long projectId, String branchName) {
        try {
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/README.md";
            Map<String, Object> fileData = new HashMap<>();
            fileData.put("file_path", "README.md");
            fileData.put("branch", branchName);
            fileData.put("content", "# " + branchName + "\n\nInitial commit");
            fileData.put("commit_message", "Initial commit");
            fileData.put("start_branch", "");
            fileData.put("author_email", "aigen@example.com");
            fileData.put("author_name", "AIGen");

            ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.POST, fileData);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Initial commit created for branch: {}", branchName);
            } else {
                log.warn("Failed to create initial commit, status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to create initial commit", e);
            throw new RuntimeException("Failed to create initial commit", e);
        }
    }

    /**
     * 触发 Pipeline
     */
    public Map<String, Object> triggerPipeline(Long projectId, String ref) {
        log.info("Triggering pipeline for project ID: {}, ref: {}", projectId, ref);

        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/pipeline";
        Map<String, Object> pipelineData = new HashMap<>();
        pipelineData.put("ref", ref);

        ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.POST, pipelineData);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> pipeline = response.getBody();
            log.info("Pipeline triggered: {} (ID: {})", pipeline.get("id"), pipeline.get("id"));
            return pipeline;
        } else {
            throw new RuntimeException("Failed to trigger pipeline");
        }
    }

    /**
     * 获取 Pipeline 状态
     */
    public PipelineStatus getPipelineStatus(Long projectId, Long pipelineId) {
        log.debug("Getting pipeline status for project ID: {}, pipeline ID: {}", projectId, pipelineId);

        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/pipelines/" + pipelineId;
        ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.GET, null);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> pipeline = response.getBody();
            String status = (String) pipeline.get("status");
            return PipelineStatus.valueOf(status.toUpperCase());
        } else {
            throw new RuntimeException("Failed to get pipeline status");
        }
    }

    /**
     * 等待 Pipeline 完成
     */
    public boolean waitForPipeline(Long projectId, Long pipelineId) throws InterruptedException {
        log.info("Waiting for pipeline to complete: project ID: {}, pipeline ID: {}", projectId, pipelineId);

        long startTime = System.currentTimeMillis();
        long interval = 5000; // 5 seconds

        while (true) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                log.error("Pipeline timeout after {} ms", timeoutMillis);
                return false;
            }

            PipelineStatus status = getPipelineStatus(projectId, pipelineId);
            log.info("Pipeline status: {}", status);

            if (status == PipelineStatus.SUCCESS) {
                log.info("Pipeline completed successfully");
                return true;
            } else if (status == PipelineStatus.FAILED || status == PipelineStatus.CANCELED) {
                log.error("Pipeline failed or canceled");
                return false;
            }

            TimeUnit.MILLISECONDS.sleep(interval);
        }
    }

    /**
     * 获取 Pipeline Artifacts
     */
    public List<Map<String, Object>> getPipelineArtifacts(Long projectId, Long pipelineId) {
        log.info("Getting pipeline artifacts for project ID: {}, pipeline ID: {}", projectId, pipelineId);

        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/pipelines/" + pipelineId + "/artifacts";
        ResponseEntity<List> response = callGitLabApiForList(url, HttpMethod.GET, null);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("Failed to get pipeline artifacts");
        }
    }

    /**
     * 创建分支
     */
    private void createBranch(Long projectId, String branchName, String ref) {
        log.debug("Creating branch: {} from ref: {}", branchName, ref);

        String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches";
        Map<String, Object> branchData = new HashMap<>();
        branchData.put("branch", branchName);
        branchData.put("ref", ref);

        ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.POST, branchData);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            log.info("Branch created: {}", branchName);
        } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
            log.info("Branch already exists: {}", branchName);
        } else {
            throw new RuntimeException("Failed to create branch: " + branchName);
        }
    }

    /**
     * 查找或创建父级群组
     */
    private Map<String, Object> findOrCreateParentGroup() {
        // 查找父级群组
        String searchUrl = gitlabUrl + "/api/v4/groups?search=" + basePath;
        ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

        if (searchResponse.getStatusCode() == HttpStatus.OK && searchResponse.getBody() != null && !searchResponse.getBody().isEmpty()) {
            List<Map<String, Object>> groups = searchResponse.getBody();
            for (Map<String, Object> group : groups) {
                if (basePath.equals(group.get("name")) || basePath.equals(group.get("path"))) {
                    return group;
                }
            }
        }

        // 创建父级群组
        log.info("Creating parent group: {}", basePath);
        String createUrl = gitlabUrl + "/api/v4/groups";
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("name", basePath);
        groupData.put("path", basePath);
        groupData.put("description", "AIGen Code Generation Projects");
        groupData.put("visibility", "private");

        ResponseEntity<Map> createResponse = callGitLabApiForMap(createUrl, HttpMethod.POST, groupData);

        if (createResponse.getStatusCode() == HttpStatus.CREATED && createResponse.getBody() != null) {
            return createResponse.getBody();
        } else {
            log.error("Failed to create parent group. Status: {}, Body: {}", createResponse.getStatusCode(), createResponse.getBody());
            throw new RuntimeException("Failed to create parent group: " + basePath);
        }
    }

    /**
     * 调用 GitLab API 的通用方法（返回列表）
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<List> callGitLabApiForList(String url, HttpMethod method, Object body) {
        return (ResponseEntity<List>) callGitLabApiInternal(url, method, body, List.class);
    }

    /**
     * 调用 GitLab API 的通用方法（返回Map）
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> callGitLabApiForMap(String url, HttpMethod method, Object body) {
        return (ResponseEntity<Map>) callGitLabApiInternal(url, method, body, Map.class);
    }

    /**
     * 调用 GitLab API 的通用方法（内部实现）
     */
    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> callGitLabApiInternal(String url, HttpMethod method, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", gitlabToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        try {
            log.debug("Calling GitLab API: {} {}", method, url);
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            
            log.debug("GitLab API response status: {}", response.getStatusCode());
            log.debug("GitLab API response body: {}", response.getBody());
            
            // 尝试解析 JSON
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    
                    // 尝试解析为数组
                    if (responseType == List.class) {
                        List list = mapper.readValue(response.getBody(), List.class);
                        return (ResponseEntity<T>) new ResponseEntity<>(list, response.getHeaders(), response.getStatusCode());
                    }
                    // 尝试解析为对象
                    else if (responseType == Map.class) {
                        Map map = mapper.readValue(response.getBody(), Map.class);
                        return (ResponseEntity<T>) new ResponseEntity<>(map, response.getHeaders(), response.getStatusCode());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse JSON response: {}", response.getBody());
                    // 如果解析失败，返回空集合
                    if (responseType == List.class) {
                        return (ResponseEntity<T>) new ResponseEntity<>(new ArrayList<>(), response.getHeaders(), response.getStatusCode());
                    } else {
                        return (ResponseEntity<T>) new ResponseEntity<>(new HashMap<>(), response.getHeaders(), response.getStatusCode());
                    }
                }
            }
            
            // 返回空集合
            if (responseType == List.class) {
                return (ResponseEntity<T>) new ResponseEntity<>(new ArrayList<>(), response.getHeaders(), response.getStatusCode());
            } else {
                return (ResponseEntity<T>) new ResponseEntity<>(new HashMap<>(), response.getHeaders(), response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("GitLab API call failed: {} {}", method, url, e);
            throw new RuntimeException("GitLab API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * Pipeline 状态枚举
     */
    public enum PipelineStatus {
        PENDING, RUNNING, SUCCESS, FAILED, CANCELED, SKIPPED, MANUAL
    }

    /**
     * 根据ID查找群组
     */
    public Map<String, Object> findGroupById(Long groupId) {
        try {
            String url = gitlabUrl + "/api/v4/groups/" + groupId;
            ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.GET, null);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to find group by ID: {}", groupId, e);
            return null;
        }
    }

    /**
     * 根据ID查找项目
     */
    public Map<String, Object> findProjectById(Long projectId) {
        try {
            String url = gitlabUrl + "/api/v4/projects/" + projectId;
            ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.GET, null);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to find project by ID: {}", projectId, e);
            return null;
        }
    }
}