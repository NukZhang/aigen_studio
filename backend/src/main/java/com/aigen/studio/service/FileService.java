package com.aigen.studio.service;

import com.aigen.studio.dto.FileNodeDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.repository.ConversationRepository;
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

    private final FileWorkspaceService fileWorkspaceService;
    private final ConversationRepository conversationRepository;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

    /**
     * 获取 Conversation 的文件树
     */
    public List<FileNodeDTO> getConversationFileTree(Long conversationId) {
        Path rootPath = resolveConversationRootIfReady(conversationId);

        if (rootPath == null) {
            return new ArrayList<>();
        }

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
        List<FileNodeDTO> children = listDirectoryChildren(path, basePath);

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
        
        boolean isDirectory = Files.isDirectory(path);
        node.setDirectory(isDirectory);
        node.setType(getFileType(path));
        if (isDirectory) {
            node.setChildren(listDirectoryChildren(path, basePath));
        }
        
        try {
            node.setSize(isDirectory ? 0L : Files.size(path));
        } catch (IOException e) {
            node.setSize(0L);
        }
        
        return node;
    }

    private List<FileNodeDTO> listDirectoryChildren(Path path, String basePath) {
        if (!Files.isDirectory(path)) {
            return new ArrayList<>();
        }

        try (var stream = Files.list(path)) {
            return stream
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
        } catch (IOException e) {
            log.warn("Failed to list directory children for {}", path, e);
            return new ArrayList<>();
        }
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
        return resolveConversationRoot(conversationId, false);
    }

    private Path resolveConversationRootIfReady(Long conversationId) {
        return resolveConversationRoot(conversationId, true);
    }

    private Path resolveConversationRoot(Long conversationId, boolean allowNotReady) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        String rootPath = conversation.getGeneratedCodePath();
        if (rootPath == null || rootPath.isBlank()) {
            if (allowNotReady) {
                return null;
            }
            throw new RuntimeException("Conversation generated code path is not ready");
        }

        Path currentRoot = Paths.get(rootPath).toAbsolutePath().normalize();
        Path configuredRoot = resolveConfiguredConversationRoot(conversationId);
        if (shouldSwitchToConfiguredRoot(currentRoot, configuredRoot)) {
            conversation.setGeneratedCodePath(configuredRoot.toString());
            conversationRepository.save(conversation);
            return configuredRoot;
        }

        return currentRoot;
    }

    private Path resolveConfiguredConversationRoot(Long conversationId) {
        return Paths.get(outputDir, "conversation-" + conversationId).toAbsolutePath().normalize();
    }

    private boolean shouldSwitchToConfiguredRoot(Path currentRoot, Path configuredRoot) {
        if (!Files.exists(configuredRoot)) {
            return false;
        }
        if (!Files.exists(currentRoot)) {
            return true;
        }

        Path backendRoot = Paths.get("").toAbsolutePath().normalize();
        Path legacyGeneratedBase = backendRoot.resolve("generated-code").normalize();
        return currentRoot.equals(backendRoot) || currentRoot.startsWith(legacyGeneratedBase);
    }
}
