package com.aigen.studio.controller;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.service.PreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewService previewService;

    @PostMapping("/conversation/{conversationId}/start")
    public ResponseEntity<PreviewStatusDTO> startPreview(@PathVariable Long conversationId) {
        log.info("Starting preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.startPreview(conversationId));
    }

    @PostMapping("/conversation/{conversationId}/stop")
    public ResponseEntity<PreviewStatusDTO> stopPreview(@PathVariable Long conversationId) {
        log.info("Stopping preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.stopPreview(conversationId));
    }

    @PostMapping("/conversation/{conversationId}/restart")
    public ResponseEntity<PreviewStatusDTO> restartPreview(@PathVariable Long conversationId) {
        log.info("Restarting preview for conversation {}", conversationId);
        return ResponseEntity.ok(previewService.restartPreview(conversationId));
    }

    @GetMapping("/conversation/{conversationId}/status")
    public ResponseEntity<PreviewStatusDTO> getStatus(@PathVariable Long conversationId) {
        return ResponseEntity.ok(previewService.getStatus(conversationId));
    }

    @GetMapping("/conversation/{conversationId}/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "100") int lines) {
        Path dataDir = Paths.get("data").toAbsolutePath();
        Path frontendLogFile = dataDir.resolve("preview-" + conversationId + "-frontend.log");
        Path backendLogFile = dataDir.resolve("preview-" + conversationId + "-backend.log");

        List<String> allLogs = new ArrayList<>();

        // 读取前端日志
        if (Files.exists(frontendLogFile)) {
            allLogs.addAll(readLastLines(frontendLogFile, lines));
        }

        // 读取后端日志
        if (Files.exists(backendLogFile)) {
            allLogs.addAll(readLastLines(backendLogFile, lines));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("logs", allLogs);
        response.put("count", allLogs.size());

        return ResponseEntity.ok(response);
    }

    private List<String> readLastLines(Path file, int lines) {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            List<String> allLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
            // 取最后N行
            int start = Math.max(0, allLines.size() - lines);
            for (int i = start; i < allLines.size(); i++) {
                result.add(allLines.get(i));
            }
        } catch (IOException e) {
            log.error("Error reading log file: {}", file, e);
        }
        return result;
    }
}
