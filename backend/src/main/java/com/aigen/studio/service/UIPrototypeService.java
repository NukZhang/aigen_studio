package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.sdk.ICodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * UI 原型生成服务
 * 负责 UI 原型的生成、保存、读取和确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UIPrototypeService {

    private final ConversationRepository conversationRepository;
    private final FileService fileService;
    private final ICodingService codingService;

    @Value("${iflow.sdk.output-dir:../../generated-code}")
    private String outputDir;

    /**
     * 生成 UI 原型
     */
    @Async
    public void generateUIPrototype(Long conversationId) {
        log.info("Generating UI prototype for conversation: {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        // 更新状态为 UI 生成中
        conversation.setStage(ConversationStage.UI_GENERATING);
        conversationRepository.save(conversation);

        // 构建 UI 生成提示词
        String prompt = buildUIPrototypePrompt(conversation);

        // 调用 iFlow SDK 生成 UI
        Path workDir = getUIPrototypeDir(conversationId);
        final Long finalConversationId = conversationId;
        try {
            Files.createDirectories(workDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create UI prototype directory: " + workDir, e);
        }
        codingService.executeTask(prompt, workDir, new ICodingService.MessageHandler() {
            private StringBuilder htmlContent = new StringBuilder();

            @Override
            public void onAssistantMessage(String text) {
                htmlContent.append(text);
            }

            @Override
            public void onToolCall(String toolName, String status) {
                log.info("Tool call: {} - {}", toolName, status);
            }

            @Override
            public void onToolResult(String content) {
                log.info("Tool result: {}", content);
            }

            @Override
            public void onTaskFinish(String stopReason) {
                log.info("UI prototype generation finished: {}", stopReason);
            }

            @Override
            public void onError(Throwable error) {
                log.error("Error generating UI prototype", error);
                Conversation conv = conversationRepository.findById(finalConversationId)
                        .orElse(null);
                if (conv != null && conv.getStage() != ConversationStage.UI_READY) {
                    conv.setStage(ConversationStage.FAILED);
                    conv.setErrorMessage("UI 原型生成失败: " + error.getMessage());
                    conversationRepository.save(conv);
                }
            }

            @Override
            public void onComplete() {
                // 保存生成的 HTML
                String html = htmlContent.toString();
                String resolvedHtml = extractHtml(html);
                Path htmlFile = getUIPrototypeDir(finalConversationId).resolve("index.html");
                if (resolvedHtml == null) {
                    try {
                        if (Files.exists(htmlFile)) {
                            String fileContent = Files.readString(htmlFile);
                            resolvedHtml = extractHtml(fileContent);
                            if (resolvedHtml != null) {
                                log.info("Loaded UI prototype HTML from file for conversation: {}", finalConversationId);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read UI prototype file for conversation: {}", finalConversationId, e);
                    }
                }

                if (resolvedHtml != null) {
                    if (!Files.exists(htmlFile)) {
                        htmlFile = saveUIPrototype(finalConversationId, resolvedHtml);
                    }
                    Conversation conv = conversationRepository.findById(finalConversationId)
                            .orElse(null);
                    if (conv != null) {
                        conv.setUiPrototypeContent(resolvedHtml);
                        conv.setUiPrototypePath(htmlFile.toString());
                        conv.setStage(ConversationStage.UI_READY);
                        conv.setErrorMessage(null);
                        conversationRepository.save(conv);
                    }
                    log.info("UI prototype saved successfully for conversation: {}", finalConversationId);
                } else {
                    log.warn("Generated content does not contain valid HTML for conversation: {}", finalConversationId);
                }
            }
        });
    }

    /**
     * 保存 UI 原型到文件系统
     */
    public Path saveUIPrototype(Long conversationId, String htmlContent) {
        try {
            Path dir = getUIPrototypeDir(conversationId);
            Files.createDirectories(dir);

            Path htmlFile = dir.resolve("index.html");
            Files.write(htmlFile, htmlContent.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("UI prototype saved to: {}", htmlFile);
            return htmlFile;
        } catch (Exception e) {
            log.error("Failed to save UI prototype for conversation: {}", conversationId, e);
            throw new RuntimeException("Failed to save UI prototype", e);
        }
    }

    /**
     * 获取 UI 原型内容
     */
    public String getUIPrototype(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        // 先从数据库读取
        if (conversation.getUiPrototypeContent() != null) {
            return conversation.getUiPrototypeContent();
        }

        // 从文件系统读取
        try {
            Path htmlFile = getUIPrototypeDir(conversationId).resolve("index.html");
            if (Files.exists(htmlFile)) {
                return Files.readString(htmlFile);
            }
        } catch (Exception e) {
            log.error("Failed to read UI prototype file for conversation: {}", conversationId, e);
        }

        return null;
    }

    /**
     * 确认 UI 设计
     */
    public void confirmUIPrototype(Long conversationId) {
        log.info("Confirming UI prototype for conversation: {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        conversation.setUiConfirmed(true);
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        log.info("UI prototype confirmed for conversation: {}", conversationId);
    }

    /**
     * 重新生成 UI 原型
     */
    public void regenerateUIPrototype(Long conversationId) {
        log.info("Regenerating UI prototype for conversation: {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        // 重置 UI 确认状态
        conversation.setUiConfirmed(false);
        conversation.setStage(ConversationStage.UI_GENERATING);
        conversation = conversationRepository.save(conversation);

        // 重新生成
        generateUIPrototype(conversationId);
    }

    /**
     * 构建 UI 生成提示词
     */
    private String buildUIPrototypePrompt(Conversation conversation) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下需求生成一个静态 HTML UI 原型页面：\n\n");
        prompt.append("需求描述：").append(conversation.getUserRequirement()).append("\n\n");
        prompt.append("AI 理解：").append(conversation.getAiUnderstanding()).append("\n\n");
        prompt.append("项目名称：").append(conversation.getProjectName()).append("\n\n");

        prompt.append("要求：\n");
        prompt.append("1. 生成一个完整的 HTML 页面（包含 DOCTYPE、html、head、body 标签）\n");
        prompt.append("2. 使用内联 CSS 样式，确保样式自包含\n");
        prompt.append("3. 页面应该现代化、美观、响应式设计\n");
        prompt.append("4. 包含所有必要的交互元素（按钮、表单、导航等）\n");
        prompt.append("5. 使用语义化 HTML 标签\n");
        prompt.append("6. 确保页面可以直接在浏览器中打开预览\n");
        prompt.append("7. 页面样式应该专业、现代、符合用户体验最佳实践\n");
        prompt.append("8. 使用适合的颜色方案（建议使用深色系或明亮的主题色）\n");
        prompt.append("9. 确保所有文本清晰可读\n");
        prompt.append("10. 输出完整的 HTML 代码，不要有额外的解释文字\n");

        return prompt.toString();
    }

    /**
     * 获取 UI 原型目录
     */
    private Path getUIPrototypeDir(Long conversationId) {
        String baseDir = outputDir.startsWith("/") ? outputDir : Paths.get(System.getProperty("user.dir"), outputDir).toString();
        return Paths.get(baseDir, "conversation-" + conversationId, "ui-prototype");
    }

    private String extractHtml(String content) {
        if (content == null) {
            return null;
        }
        int htmlEnd = content.lastIndexOf("</html>");
        if (htmlEnd < 0) {
            return null;
        }

        int docTypeIndex = indexOfIgnoreCase(content, "<!doctype");
        int htmlStart = content.indexOf("<html");
        int start = htmlStart;
        if (docTypeIndex >= 0 && (htmlStart < 0 || docTypeIndex < htmlStart)) {
            start = docTypeIndex;
        }
        if (start < 0 || htmlEnd <= start) {
            return null;
        }

        return content.substring(start, htmlEnd + "</html>".length());
    }

    private int indexOfIgnoreCase(String content, String needle) {
        return content.toLowerCase().indexOf(needle.toLowerCase());
    }
}
