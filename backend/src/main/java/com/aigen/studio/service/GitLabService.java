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

    private final RestTemplate restTemplate;

    /**
     * 创建或获取 GitLab 群组
     */
    public Map<String, Object> createOrGetGroup(String groupName, String description) {
        log.info("Creating or getting GitLab group: {}", groupName);

        // 查找群组
        String searchUrl = gitlabUrl + "/api/v4/groups?search=" + groupName + "";
        ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

        if (searchResponse.getStatusCode() == HttpStatus.OK && searchResponse.getBody() != null && !searchResponse.getBody().isEmpty()) {
            List<Map<String, Object>> groups = searchResponse.getBody();
            for (Map<String, Object> group : groups) {
                if (groupName.equals(group.get("name")) || groupName.equals(group.get("path"))) {
                    log.info("Group already exists: {} (ID: {})", groupName, group.get("id"));
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
        log.info("Creating new group: {}", groupName);
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
        } else if (createResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            // 群组已存在，再次尝试查找
            log.warn("Failed to create group, trying to find existing one. Status: {}, Body: {}",
                createResponse.getStatusCode(), createResponse.getBody());

            try {
                Thread.sleep(1000); // 等待1秒
                searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

                if (searchResponse.getStatusCode() == HttpStatus.OK && searchResponse.getBody() != null && !searchResponse.getBody().isEmpty()) {
                    List<Map<String, Object>> groups = searchResponse.getBody();
                    for (Map<String, Object> group : groups) {
                        if (groupName.equals(group.get("name")) || groupName.equals(group.get("path"))) {
                            log.info("Found existing group after creation attempt: {} (ID: {})", groupName, group.get("id"));
                            return group;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error while retrying to find group", e);
            }

            throw new RuntimeException("Failed to create group and could not find existing one: " + groupName);
        } else {
            log.error("Failed to create group. Status: {}, Body: {}", createResponse.getStatusCode(), createResponse.getBody());
            throw new RuntimeException("Failed to create group: " + groupName);
        }
    }

    /**
     * 创建或获取 GitLab 项目
     */
    public Map<String, Object> createOrGetProject(Long groupId, String projectName, String description) {
        log.info("Creating or getting GitLab project: {} in group {}", projectName, groupId);

        // 查找项目 - 在指定群组下搜索项目
        String searchUrl = gitlabUrl + "/api/v4/groups/" + groupId + "/projects?search=" + projectName + "&include_subgroups=false";
        log.info("Searching for project in group {} with URL: {}", groupId, searchUrl);
        log.info("Search parameters:");
        log.info("  groupId: {}", groupId);
        log.info("  projectName: {}", projectName);
        log.info("  include_subgroups: false");
        ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);
        log.info("Search response status: {}, body size: {}", searchResponse.getStatusCode(), searchResponse.getBody() != null ? searchResponse.getBody().size() : 0);

        if (searchResponse.getStatusCode() == HttpStatus.OK && !searchResponse.getBody().isEmpty()) {
            log.info("Found {} projects in search results", searchResponse.getBody().size());
            List<Map<String, Object>> projects = searchResponse.getBody();
            int index = 0;
            for (Map<String, Object> project : projects) {
                index++;
                String path = (String) project.get("path");
                String name = (String) project.get("name");
                log.info("Checking project [{}]: name={}, path={}, projectName={}", index, name, path, projectName);
                log.info("Project details [{}]:", index);
                log.info("  id: {}", project.get("id"));
                log.info("  name: {}", project.get("name"));
                log.info("  path: {}", project.get("path"));
                log.info("  path_with_namespace: {}", project.get("path_with_namespace"));
                log.info("  namespace: {}", project.get("namespace"));
                log.info("  Full project entity: {}", project);

                if (projectName.equals(name) || projectName.equals(path)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> namespace = (Map<String, Object>) project.get("namespace");
                    log.info("Name/path matched. Namespace: {}, groupId={}", namespace, groupId);
                    log.info("Namespace details:");
                    if (namespace != null) {
                        log.info("  kind: {}", namespace.get("kind"));
                        log.info("  id: {}", namespace.get("id"));
                        log.info("  name: {}", namespace.get("name"));
                        log.info("  path: {}", namespace.get("path"));
                        log.info("  full namespace entity: {}", namespace);
                    } else {
                        log.info("  namespace is null");
                    }
                    // 检查 namespace 的 ID 是否匹配 groupId
                    if (namespace != null && groupId.equals(namespace.get("id"))) {
                        log.info("Project already exists: {} (ID: {})", projectName, project.get("id"));
                        return project;
                    } else {
                        log.warn("Namespace ID mismatch. namespaceId={}, groupId={}", 
                            namespace != null ? namespace.get("id") : "null", 
                            groupId);
                    }
                }
            }
        } else {
            log.warn("Search returned no results or error. Status: {}, body empty: {}", 
                searchResponse.getStatusCode(), searchResponse.getBody() == null || searchResponse.getBody().isEmpty());
            log.warn("Search URL: {}", searchUrl);
        }

        // 创建新项目
        log.info("Creating new project: {}", projectName);
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
        projectData.put("default_branch", "v.1.0.0"); // 设置默认分支为v.1.0.0
        projectData.put("only_allow_merge_if_pipeline_succeeds", false); // 不要求 pipeline 成功才能合并
        projectData.put("printing_merge_request_link_enabled", false);

        // 输出完整的创建参数用于问题复现
        log.info("Creating project with parameters:");
        log.info("  URL: {}", createUrl);
        log.info("  name: {}", projectData.get("name"));
        log.info("  path: {}", projectData.get("path"));
        log.info("  description: {}", projectData.get("description"));
        log.info("  namespace_id: {}", projectData.get("namespace_id"));
        log.info("  visibility: {}", projectData.get("visibility"));
        log.info("  issues_enabled: {}", projectData.get("issues_enabled"));
        log.info("  wiki_enabled: {}", projectData.get("wiki_enabled"));
        log.info("  snippets_enabled: {}", projectData.get("snippets_enabled"));
        log.info("  merge_requests_enabled: {}", projectData.get("merge_requests_enabled"));
        log.info("  Full entity: {}", projectData);

        ResponseEntity<Map> createResponse = callGitLabApiForMap(createUrl, HttpMethod.POST, projectData);

        if (createResponse.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> createdProject = createResponse.getBody();
            log.info("Project created successfully: {} (ID: {})", projectName, createdProject.get("id"));
            return createdProject;
        } else if (createResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            // 项目已存在，再次尝试查找
            log.error("GitLab API call failed: POST {}", createUrl);
            log.error("Failed to create project. Status: {}", createResponse.getStatusCode());
            log.error("Request entity:");
            log.error("  name: {}", projectData.get("name"));
            log.error("  path: {}", projectData.get("path"));
            log.error("  description: {}", projectData.get("description"));
            log.error("  namespace_id: {}", projectData.get("namespace_id"));
            log.error("  visibility: {}", projectData.get("visibility"));
            log.error("  issues_enabled: {}", projectData.get("issues_enabled"));
            log.error("  wiki_enabled: {}", projectData.get("wiki_enabled"));
            log.error("  snippets_enabled: {}", projectData.get("snippets_enabled"));
            log.error("  merge_requests_enabled: {}", projectData.get("merge_requests_enabled"));
            log.error("  Full entity: {}", projectData);
            log.error("Response body: {}", createResponse.getBody());
            log.warn("Trying to find existing project after failed creation attempt");

            try {
                Thread.sleep(1000); // 等待1秒
                searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

                if (searchResponse.getStatusCode() == HttpStatus.OK && !searchResponse.getBody().isEmpty()) {
                    List<Map<String, Object>> projects = searchResponse.getBody();
                    for (Map<String, Object> project : projects) {
                        if (projectName.equals(project.get("name")) || projectName.equals(project.get("path"))) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> namespace = (Map<String, Object>) project.get("namespace");
                            if (namespace != null && groupId.equals(namespace.get("id"))) {
                                log.info("Found existing project after creation attempt: {} (ID: {})", projectName, project.get("id"));
                                return project;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error while retrying to find project", e);
            }

            throw new RuntimeException("Failed to create project and could not find existing one: " + projectName);
        } else {
            throw new RuntimeException("Failed to create project: " + projectName + ", status: " + createResponse.getStatusCode());
        }
    }

    /**
     * 推送代码到 GitLab 项目
     */
    public void pushCode(Long projectId, String branchName, File sourceDir, String commitMessage) {
        log.info("Pushing code to project ID: {}, branch: {}", projectId, branchName);

        try {
            // 禁用分支保护，允许推送到任何分支
            disableBranchProtection(projectId, branchName);

            // 检查项目是否有任何分支
            boolean hasBranches = checkProjectHasBranches(projectId);

            // 如果没有分支，这是新项目，需要创建初始提交
            if (!hasBranches) {
                log.info("Project has no branches, creating initial commit");
                createInitialCommit(projectId, branchName);
                // 等待一下，确保初始提交完成
                Thread.sleep(1000);
            } else {
                // 检查目标分支是否存在
                boolean branchExists = checkBranchExists(projectId, branchName);
                if (!branchExists) {
                    log.info("Branch {} does not exist, creating from main", branchName);
                    createBranch(projectId, branchName, "main");
                }
            }

            // 遍历源目录中的所有文件
            Path sourcePath = sourceDir.toPath();
            List<String> filesToUpload = new ArrayList<>();

            Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                        filesToUpload.add(relativePath);
                    } catch (Exception e) {
                        log.error("Failed to process file: {}", file, e);
                    }
                });

            log.info("Found {} files to upload", filesToUpload.size());

            // 上传每个文件
            int successCount = 0;
            int failCount = 0;

            for (String relativePath : filesToUpload) {
                try {
                    Path file = sourcePath.resolve(relativePath);
                    String content = Files.readString(file);

                    // 上传文件 - 需要对路径进行 URL 编码
                    String encodedPath = java.net.URLEncoder.encode(relativePath, "UTF-8");
                    String fileUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/" + encodedPath;

                    // 先检查文件是否已存在
                    boolean fileExists = checkFileExists(projectId, branchName, relativePath);

                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("file_path", relativePath);
                    fileData.put("branch", branchName);
                    fileData.put("content", content);
                    fileData.put("commit_message", commitMessage + " - " + relativePath);

                    ResponseEntity<Map> response;
                    if (fileExists) {
                        // 文件已存在，使用 PUT 更新
                        response = callGitLabApiForMap(fileUrl, HttpMethod.PUT, fileData);
                        log.debug("Updated file: {}", relativePath);
                    } else {
                        // 文件不存在，使用 POST 创建
                        response = callGitLabApiForMap(fileUrl, HttpMethod.POST, fileData);
                        log.debug("Created file: {}", relativePath);
                    }

                    if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                        log.info("File uploaded: {}", relativePath);
                        successCount++;
                    } else {
                        log.error("Failed to upload file: {} (Status: {}, Body: {})",
                            relativePath, response.getStatusCode(), response.getBody());
                        failCount++;
                    }

                } catch (Exception e) {
                    log.error("Failed to push file: {}", relativePath, e);
                    failCount++;
                }
            }

            log.info("File upload completed: {} succeeded, {} failed", successCount, failCount);

            if (failCount > 0) {
                log.warn("Some files failed to upload, but continuing...");
            }

        } catch (Exception e) {
            log.error("Failed to push code to project ID: {}", projectId, e);
            throw new RuntimeException("Failed to push code", e);
        }
    }

    /**
     * 检查项目是否有任何分支
     */
    public boolean checkProjectHasBranches(Long projectId) {
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
     * 检查分支是否存在
     */
    private boolean checkBranchExists(Long projectId, String branchName) {
        try {
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/branches/" + branchName;
            ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.GET, null);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Branch {} does not exist or error checking: {}", branchName, e.getMessage());
            return false;
        }
    }

    /**
     * 检查文件是否存在
     */
    private boolean checkFileExists(Long projectId, String branchName, String filePath) {
        try {
            String encodedPath = java.net.URLEncoder.encode(filePath, "UTF-8");
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/repository/files/" + encodedPath + "?ref=" + branchName;
            ResponseEntity<Map> response = callGitLabApiForMap(url, HttpMethod.GET, null);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("File {} does not exist or error checking: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 禁用分支保护，允许直接推送
     */
    private void disableBranchProtection(Long projectId, String branchName) {
        try {
            // 获取所有受保护的分支
            String url = gitlabUrl + "/api/v4/projects/" + projectId + "/protected_branches";
            ResponseEntity<List> response = callGitLabApiForList(url, HttpMethod.GET, null);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> protectedBranches = response.getBody();

                for (Map<String, Object> branch : protectedBranches) {
                    String protectedBranchName = (String) branch.get("name");

                    // 如果目标分支被保护，则删除保护
                    if (branchName.equals(protectedBranchName)) {
                        log.info("Branch {} is protected, removing protection", branchName);
                        String deleteUrl = gitlabUrl + "/api/v4/projects/" + projectId + "/protected_branches/"
                            + java.net.URLEncoder.encode(branchName, "UTF-8");

                        ResponseEntity<Map> deleteResponse = callGitLabApiForMap(deleteUrl, HttpMethod.DELETE, null);

                        if (deleteResponse.getStatusCode() == HttpStatus.OK || deleteResponse.getStatusCode() == HttpStatus.NO_CONTENT) {
                            log.info("Branch protection removed for: {}", branchName);
                        } else {
                            log.warn("Failed to remove branch protection for: {} (Status: {})",
                                branchName, deleteResponse.getStatusCode());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to disable branch protection: {}", e.getMessage());
            // 不抛出异常，因为分支保护可能不存在
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
            log.error("Request details:");
            log.error("  URL: {}", url);
            log.error("  Method: {}", method);
            if (body != null) {
                log.error("  Request body: {}", body);
            }
            log.error("  Error message: {}", e.getMessage());
            log.error("  Error type: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("  Cause: {}", e.getCause().getMessage());
            }
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
     * 根据名称查找群组
     */
    public Map<String, Object> findGroupByName(String groupName) {
        try {
            String searchUrl = gitlabUrl + "/api/v4/groups?search=" + groupName;
            ResponseEntity<List> searchResponse = callGitLabApiForList(searchUrl, HttpMethod.GET, null);

            if (searchResponse.getStatusCode() == HttpStatus.OK && searchResponse.getBody() != null && !searchResponse.getBody().isEmpty()) {
                List<Map<String, Object>> groups = searchResponse.getBody();
                for (Map<String, Object> group : groups) {
                    if (groupName.equals(group.get("name")) || groupName.equals(group.get("path"))) {
                        return group;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to find group by name: {}", groupName, e);
            return null;
        }
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