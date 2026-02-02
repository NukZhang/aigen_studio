package com.aigen.studio.controller;

import com.aigen.studio.dto.ArtifactDTO;
import com.aigen.studio.entity.Artifact;
import com.aigen.studio.service.ArtifactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/artifacts")
@RequiredArgsConstructor
@Slf4j
public class ArtifactController {

    private final ArtifactService artifactService;

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ArtifactDTO>> getArtifactsByJobId(@PathVariable Long jobId) {
        return ResponseEntity.ok(artifactService.getArtifactsByJobId(jobId));
    }

    @GetMapping("/job/{jobId}/type/{type}")
    public ResponseEntity<List<ArtifactDTO>> getArtifactsByType(
            @PathVariable Long jobId,
            @PathVariable String type) {
        try {
            return ResponseEntity.ok(artifactService.getArtifactsByType(jobId, type.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ArtifactDTO>> getAllArtifacts() {
        return ResponseEntity.ok(artifactService.getAllArtifacts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArtifactDTO> getArtifact(@PathVariable Long id) {
        return artifactService.getArtifactById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/deliver-gitlab")
    public ResponseEntity<String> deliverToGitLab(@PathVariable Long id) {
        try {
            artifactService.deliverArtifactToGitLab(id);
            return ResponseEntity.ok("交付到 GitLab 成功");
        } catch (Exception e) {
            log.error("交付到 GitLab 失败", e);
            return ResponseEntity.badRequest().body("交付失败: " + e.getMessage());
        }
    }

    /**
     * 临时修复方法：为job补充缺失的artifacts
     * 扫描生成的文件目录，为所有文件创建artifacts
     */
    @PostMapping("/fix-job/{jobId}")
    public ResponseEntity<Map<String, Object>> fixJobArtifacts(@PathVariable Long jobId) {
        try {
            log.info("Starting to fix artifacts for job: {}", jobId);

            // 获取job的PROJECT类型artifact
            List<ArtifactDTO> projectArtifacts = artifactService.getArtifactsByType(jobId, "PROJECT");
            if (projectArtifacts.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "No PROJECT artifact found for job " + jobId
                ));
            }

            String projectPath = projectArtifacts.get(0).getPath();
            Path outputPath = Paths.get(projectPath).toAbsolutePath();

            if (!Files.exists(outputPath)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Output directory does not exist: " + outputPath
                ));
            }

            // 扫描所有文件
            List<String> generatedFiles = new java.util.ArrayList<>();
            Files.walk(outputPath)
                .filter(Files::isRegularFile)
                .forEach(file -> generatedFiles.add(file.toAbsolutePath().toString()));

            log.info("Found {} files in {}", generatedFiles.size(), outputPath);

            // 创建artifacts
            int frontendCount = 0;
            int backendCount = 0;
            int otherCount = 0;

            for (String filePath : generatedFiles) {
                Path file = Paths.get(filePath);
                Path absoluteFile = file.isAbsolute() ? file : file.toAbsolutePath();
                String relativePath = outputPath.relativize(absoluteFile).toString();

                String type;
                String description;

                if (relativePath.contains("openapi.yaml")) {
                    type = Artifact.ArtifactType.OPENAPI_SPEC.name();
                    description = "OpenAPI specification";
                } else if (relativePath.startsWith("frontend/")) {
                    type = Artifact.ArtifactType.FRONTEND_CODE.name();
                    description = "Frontend code";
                    frontendCount++;
                } else if (relativePath.startsWith("backend/")) {
                    type = Artifact.ArtifactType.BACKEND_CODE.name();
                    description = "Backend code";
                    backendCount++;
                } else {
                    continue; // 跳过其他文件
                }

                try {
                    artifactService.createArtifact(
                        jobId,
                        relativePath,
                        type,
                        filePath,
                        description,
                        Files.size(file)
                    );
                    log.info("Created artifact: {}", relativePath);
                } catch (Exception e) {
                    log.warn("Failed to create artifact for {}: {}", relativePath, e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("jobId", jobId);
            result.put("totalFiles", generatedFiles.size());
            result.put("frontendArtifacts", frontendCount);
            result.put("backendArtifacts", backendCount);
            result.put("message", "Artifacts fixed successfully");

            log.info("Fixed artifacts for job {}: {} frontend, {} backend",
                jobId, frontendCount, backendCount);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to fix artifacts for job: {}", jobId, e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to fix artifacts: " + e.getMessage()
            ));
        }
    }
}