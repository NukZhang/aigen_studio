package com.aigen.studio.controller;

import com.aigen.studio.dto.FileNodeDTO;
import com.aigen.studio.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件控制器
 * 提供文件浏览和读取功能
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 获取 Job 的文件树
     */
    @GetMapping("/job/{jobId}/tree")
    public ResponseEntity<List<FileNodeDTO>> getFileTree(@PathVariable Long jobId) {
        log.info("Getting file tree for job: {}", jobId);
        List<FileNodeDTO> fileTree = fileService.getFileTree(jobId);
        return ResponseEntity.ok(fileTree);
    }

    /**
     * 获取文件内容
     */
    @GetMapping("/job/{jobId}/content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @PathVariable Long jobId,
            @RequestParam String filePath) {
        log.info("Getting file content for job {}, file: {}", jobId, filePath);
        
        try {
            String content = fileService.getFileContent(jobId, filePath);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "content", content,
                    "filePath", filePath
            ));
        } catch (Exception e) {
            log.error("Failed to get file content", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}