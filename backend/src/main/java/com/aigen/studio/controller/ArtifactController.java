package com.aigen.studio.controller;

import com.aigen.studio.dto.ArtifactDTO;
import com.aigen.studio.entity.Artifact;
import com.aigen.studio.service.ArtifactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            Artifact.ArtifactType artifactType = Artifact.ArtifactType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(artifactService.getArtifactsByType(jobId, artifactType));
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
}