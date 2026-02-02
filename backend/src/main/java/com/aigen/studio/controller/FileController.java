package com.aigen.studio.controller;

import com.aigen.studio.dto.FileNodeDTO;
import com.aigen.studio.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 获取 Conversation 的文件树
     */
    @GetMapping("/conversation/{conversationId}/tree")
    public ResponseEntity<List<FileNodeDTO>> getConversationFileTree(@PathVariable Long conversationId) {
        log.info("Getting file tree for conversation: {}", conversationId);
        List<FileNodeDTO> fileTree = fileService.getConversationFileTree(conversationId);
        return ResponseEntity.ok(fileTree);
    }

    /**
     * 获取 Conversation 文件内容
     */
    @GetMapping("/conversation/{conversationId}/content")
    public ResponseEntity<Map<String, Object>> getConversationFileContent(
            @PathVariable Long conversationId,
            @RequestParam String filePath) {
        log.info("Getting file content for conversation {}, file: {}", conversationId, filePath);

        try {
            String content = fileService.getConversationFileContent(conversationId, filePath);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "content", content,
                    "filePath", filePath
            ));
        } catch (Exception e) {
            log.error("Failed to get conversation file content", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 保存 Conversation 文件内容
     */
    @PutMapping("/conversation/{conversationId}/content")
    public ResponseEntity<Map<String, Object>> saveConversationFileContent(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> payload) {
        String filePath = payload.get("filePath");
        String content = payload.getOrDefault("content", "");
        log.info("Saving file content for conversation {}, file: {}", conversationId, filePath);

        try {
            fileService.saveConversationFileContent(conversationId, filePath, content);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "filePath", filePath
            ));
        } catch (Exception e) {
            log.error("Failed to save conversation file content", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 创建 Conversation 文件
     */
    @PostMapping("/conversation/{conversationId}/create")
    public ResponseEntity<Map<String, Object>> createConversationFile(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> payload) {
        String filePath = payload.get("filePath");
        log.info("Creating file for conversation {}, file: {}", conversationId, filePath);

        try {
            fileService.createConversationFile(conversationId, filePath);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "filePath", filePath
            ));
        } catch (Exception e) {
            log.error("Failed to create conversation file", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 创建 Conversation 目录
     */
    @PostMapping("/conversation/{conversationId}/mkdir")
    public ResponseEntity<Map<String, Object>> createConversationDirectory(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> payload) {
        String path = payload.get("path");
        log.info("Creating directory for conversation {}, path: {}", conversationId, path);

        try {
            fileService.createConversationDirectory(conversationId, path);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "path", path
            ));
        } catch (Exception e) {
            log.error("Failed to create conversation directory", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 重命名 Conversation 文件/目录
     */
    @PostMapping("/conversation/{conversationId}/rename")
    public ResponseEntity<Map<String, Object>> renameConversationPath(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> payload) {
        String from = payload.get("from");
        String to = payload.get("to");
        log.info("Renaming path for conversation {}: {} -> {}", conversationId, from, to);

        try {
            fileService.renameConversationPath(conversationId, from, to);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "from", from,
                    "to", to
            ));
        } catch (Exception e) {
            log.error("Failed to rename conversation path", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 删除 Conversation 文件/目录
     */
    @PostMapping("/conversation/{conversationId}/delete")
    public ResponseEntity<Map<String, Object>> deleteConversationPath(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> payload) {
        String path = payload.get("path");
        log.info("Deleting path for conversation {}, path: {}", conversationId, path);

        try {
            fileService.deleteConversationPath(conversationId, path);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "path", path
            ));
        } catch (Exception e) {
            log.error("Failed to delete conversation path", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 上传 Conversation 文件
     */
    @PostMapping("/conversation/{conversationId}/upload")
    public ResponseEntity<Map<String, Object>> uploadConversationFile(
            @PathVariable Long conversationId,
            @RequestParam(value = "targetDir", required = false) String targetDir,
            @RequestParam("file") MultipartFile file) {
        log.info("Uploading file for conversation {}, targetDir: {}", conversationId, targetDir);

        try {
            fileService.uploadConversationFile(conversationId, targetDir, file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "filePath", file.getOriginalFilename()
            ));
        } catch (Exception e) {
            log.error("Failed to upload conversation file", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
