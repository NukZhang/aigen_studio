package com.aigen.studio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教程服务
 * 读取 docs/tutorials 目录中的教程文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorialService {

    @Value("${tutorial.base-path:./tutorials}")
    private String tutorialBasePath;

    /**
     * 获取教程目录树
     */
    public List<Map<String, Object>> getTutorialTree() {
        Path rootPath = resolveTutorialRoot();

        if (!Files.exists(rootPath)) {
            log.warn("Tutorial directory does not exist: {}", rootPath);
            return new ArrayList<>();
        }

        try {
            return buildFileTree(rootPath, rootPath.toString());
        } catch (IOException e) {
            log.error("Failed to build tutorial tree", e);
            throw new RuntimeException("Failed to build tutorial tree", e);
        }
    }

    /**
     * 获取教程文件内容
     */
    public String getTutorialContent(String filePath) {
        Path rootPath = resolveTutorialRoot();
        Path fullPath = rootPath.resolve(filePath).normalize();

        // 安全检查：确保文件在教程目录内
        if (!fullPath.startsWith(rootPath)) {
            throw new RuntimeException("Invalid file path: " + filePath);
        }

        if (!Files.exists(fullPath)) {
            throw new RuntimeException("File not found: " + filePath);
        }

        if (!Files.isRegularFile(fullPath)) {
            throw new RuntimeException("Not a regular file: " + filePath);
        }

        try {
            return Files.readString(fullPath);
        } catch (IOException e) {
            log.error("Failed to read tutorial file: {}", fullPath, e);
            throw new RuntimeException("Failed to read tutorial file", e);
        }
    }

    /**
     * 解析教程根目录
     */
    private Path resolveTutorialRoot() {
        Path path = Paths.get(tutorialBasePath).toAbsolutePath().normalize();
        return path;
    }

    /**
     * 构建文件树
     */
    private List<Map<String, Object>> buildFileTree(Path path, String basePath) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        if (Files.isRegularFile(path)) {
            return List.of(createFileNode(path, basePath));
        }

        // 目录：递归处理子节点
        List<Map<String, Object>> children = listDirectoryChildren(path, basePath);

        return List.of(createDirectoryNode(path, basePath, children));
    }

    /**
     * 创建目录节点
     */
    private Map<String, Object> createDirectoryNode(Path path, String basePath, List<Map<String, Object>> children) {
        String name = path.getFileName().toString();
        String nodePath = basePath.equals(path.toString()) ? "" : path.toString().substring(basePath.length() + 1);

        return Map.of(
                "name", name,
                "path", nodePath,
                "type", "directory",
                "children", children
        );
    }

    /**
     * 创建文件节点
     */
    private Map<String, Object> createFileNode(Path path, String basePath) {
        String name = path.getFileName().toString();
        String relativePath = path.toString().substring(basePath.length() + 1);

        return Map.of(
                "name", name,
                "path", relativePath,
                "type", "file"
        );
    }

    /**
     * 列出目录子节点
     */
    private List<Map<String, Object>> listDirectoryChildren(Path path, String basePath) {
        if (!Files.isDirectory(path)) {
            return new ArrayList<>();
        }

        try (var stream = Files.list(path)) {
            return stream
                    .filter(p -> !p.getFileName().toString().startsWith(".")) // 过滤隐藏文件
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
                    .map(p -> createNode(p, basePath))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to list directory children for {}", path, e);
            return new ArrayList<>();
        }
    }

    /**
     * 创建节点（文件或目录）
     */
    private Map<String, Object> createNode(Path path, String basePath) {
        if (Files.isDirectory(path)) {
            return createDirectoryNode(path, basePath, listDirectoryChildren(path, basePath));
        } else {
            return createFileNode(path, basePath);
        }
    }

    /**
     * 从 Markdown 内容中提取标题树
     */
    public List<Map<String, Object>> extractHeadings(String markdownContent) {
        List<Map<String, Object>> headings = new ArrayList<>();
        java.util.Stack<Map<String, Object>> stack = new java.util.Stack<>();

        String[] lines = markdownContent.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // 匹配 Markdown 标题 (#, ##, ###, ####, #####, ######)
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }

                if (level > 6) continue; // Markdown 只支持 6 级标题

                // 提取标题文本（去掉 # 号和前导空格）
                String text = line.substring(level).trim();
                if (text.isEmpty()) continue;

                // 生成锚点 ID
                String anchor = generateAnchor(text);

                Map<String, Object> heading = new java.util.HashMap<>();
                heading.put("level", level);
                heading.put("text", text);
                heading.put("anchor", anchor);
                heading.put("children", new ArrayList<Map<String, Object>>());

                // 根据层级找到父级标题
                while (!stack.isEmpty() && (int) stack.peek().get("level") >= level) {
                    stack.pop();
                }

                if (stack.isEmpty()) {
                    headings.add(heading);
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children =
                        (List<Map<String, Object>>) stack.peek().get("children");
                    children.add(heading);
                }

                stack.push(heading);
            }
        }

        return headings;
    }

    /**
     * 生成锚点 ID（用于标题跳转）
     */
    private String generateAnchor(String text) {
        // 转小写，移除特殊字符，空格替换为连字符
        return text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s\\u4e00-\\u9fa5]", "")
                .replaceAll("\\s+", "-");
    }
}