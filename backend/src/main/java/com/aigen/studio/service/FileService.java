package com.aigen.studio.service;

import com.aigen.studio.dto.FileNodeDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.GenerationJob;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件服务
 * 读取 generated-code 目录中的文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final GenerationJobRepository generationJobRepository;
    private final FileWorkspaceService fileWorkspaceService;
    private final ConversationRepository conversationRepository;

    @Value("${iflow.sdk.output-dir:../../generated-code}")
    private String outputDir;

    /**
     * 获取 Job 的文件树
     */
    public List<FileNodeDTO> getFileTree(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        Path jobPath = Paths.get(outputDir, "job-" + jobId);

        if (!Files.exists(jobPath)) {
            log.warn("Job directory does not exist: {}", jobPath);
            return new ArrayList<>();
        }

        try {
            return buildFileTree(jobPath, jobPath.toString());
        } catch (IOException e) {
            log.error("Failed to build file tree for job {}", jobId, e);
            throw new RuntimeException("Failed to build file tree", e);
        }
    }

    /**
     * 获取文件内容
     */
    public String getFileContent(Long jobId, String filePath) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // 安全检查：确保文件路径在 Job 目录内
        Path jobPath = Paths.get(outputDir, "job-" + jobId).normalize();
        Path targetPath = Paths.get(outputDir, "job-" + jobId, filePath).normalize();

        if (!targetPath.startsWith(jobPath)) {
            throw new RuntimeException("Invalid file path");
        }

        if (!Files.exists(targetPath) || Files.isDirectory(targetPath)) {
            throw new RuntimeException("File not found: " + filePath);
        }

        try {
            return Files.readString(targetPath);
        } catch (IOException e) {
            log.error("Failed to read file: {}", targetPath, e);
            throw new RuntimeException("Failed to read file", e);
        }
    }

    /**
     * 获取 Conversation 的文件树
     */
    public List<FileNodeDTO> getConversationFileTree(Long conversationId) {
        Path rootPath = resolveConversationRoot(conversationId);

        if (!Files.exists(rootPath)) {
            log.warn("Conversation directory does not exist: {}", rootPath);
            return new ArrayList<>();
        }

        try {
            return buildFileTree(rootPath, rootPath.toString());
        } catch (IOException e) {
            log.error("Failed to build file tree for conversation {}", conversationId, e);
            throw new RuntimeException("Failed to build file tree", e);
        }
    }

    /**
     * 获取 Conversation 文件内容
     */
    public String getConversationFileContent(Long conversationId, String filePath) {
        Path rootPath = resolveConversationRoot(conversationId);
        return fileWorkspaceService.readText(rootPath, filePath);
    }

    /**
     * 保存 Conversation 文件内容
     */
    public void saveConversationFileContent(Long conversationId, String filePath, String content) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.writeText(rootPath, filePath, content);
    }

    /**
     * 创建 Conversation 文件
     */
    public void createConversationFile(Long conversationId, String filePath) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.createFile(rootPath, filePath);
    }

    /**
     * 创建 Conversation 目录
     */
    public void createConversationDirectory(Long conversationId, String path) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.createDirectory(rootPath, path);
    }

    /**
     * 重命名 Conversation 文件/目录
     */
    public void renameConversationPath(Long conversationId, String from, String to) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.renamePath(rootPath, from, to);
    }

    /**
     * 删除 Conversation 文件/目录
     */
    public void deleteConversationPath(Long conversationId, String path) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.deletePath(rootPath, path);
    }

    /**
     * 上传 Conversation 文件
     */
    public void uploadConversationFile(Long conversationId, String targetDir, MultipartFile file) {
        Path rootPath = resolveConversationRoot(conversationId);
        fileWorkspaceService.saveUpload(rootPath, targetDir, file);
    }

    /**
     * 构建文件树
     */
    private List<FileNodeDTO> buildFileTree(Path path, String basePath) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        if (Files.isRegularFile(path)) {
            return List.of(createFileNode(path, basePath));
        }

        // 目录：递归处理子节点
        List<FileNodeDTO> children = new ArrayList<>();
        try (var stream = Files.list(path)) {
            children = stream
                    .sorted((p1, p2) -> {
                        // 目录优先
                        boolean p1Dir = Files.isDirectory(p1);
                        boolean p2Dir = Files.isDirectory(p2);
                        if (p1Dir != p2Dir) {
                            return p1Dir ? -1 : 1;
                        }
                        // 同类型按名称排序
                        return p1.getFileName().toString().compareTo(p2.getFileName().toString());
                    })
                    .map(p -> createFileNode(p, basePath))
                    .collect(Collectors.toList());
        }

        FileNodeDTO directoryNode = new FileNodeDTO();
        directoryNode.setName(path.getFileName().toString());
        directoryNode.setPath(basePath.equals(path.toString()) ? "" : path.toString().substring(basePath.length() + 1));
        directoryNode.setDirectory(true);
        directoryNode.setType("directory");
        directoryNode.setSize(0L);
        directoryNode.setChildren(children);

        return List.of(directoryNode);
    }

    /**
     * 创建文件节点
     */
    private FileNodeDTO createFileNode(Path path, String basePath) {
        FileNodeDTO node = new FileNodeDTO();
        node.setName(path.getFileName().toString());
        
        String relativePath = path.toString().substring(basePath.length() + 1);
        node.setPath(relativePath);
        
        node.setDirectory(Files.isDirectory(path));
        node.setType(getFileType(path));
        
        try {
            node.setSize(Files.isDirectory(path) ? 0L : Files.size(path));
        } catch (IOException e) {
            node.setSize(0L);
        }
        
        return node;
    }

    /**
     * 获取文件类型
     */
    private String getFileType(Path path) {
        if (Files.isDirectory(path)) {
            return "directory";
        }

        String fileName = path.getFileName().toString();
        String extension = getFileExtension(fileName);

        return switch (extension.toLowerCase()) {
            case "js", "ts", "jsx", "tsx" -> "javascript";
            case "java" -> "java";
            case "py" -> "python";
            case "html", "htm" -> "html";
            case "css", "scss", "sass", "less" -> "css";
            case "json" -> "json";
            case "xml", "yaml", "yml" -> "xml";
            case "md" -> "markdown";
            case "txt" -> "text";
            case "png", "jpg", "jpeg", "gif", "svg", "ico" -> "image";
            case "pdf" -> "pdf";
            case "zip", "tar", "gz", "rar" -> "archive";
            default -> "unknown";
        };
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    private Path resolveConversationRoot(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        String rootPath = conversation.getGeneratedCodePath();
        if (rootPath == null || rootPath.isBlank()) {
            throw new RuntimeException("Conversation generated code path is not ready");
        }
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }
}
