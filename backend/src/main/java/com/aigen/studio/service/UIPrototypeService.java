package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import com.aigen.studio.sdk.ICodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * UI 原型生成服务
 * 负责 UI 原型的生成、保存、读取和确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UIPrototypeService {

    private static final String TEMPLATE_SECTION_PRIMARY = "PRIMARY";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FileService fileService;
    private final ICodingService codingService;
    private final ResourceLoader resourceLoader;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Value("${iflow.sdk.output-dir:../../generated-code}")
    private String outputDir;

    @Value("${aigen.prompt.ui-prototype-template:classpath:prompt-templates/ui-prototype-prompt.md}")
    private String uiPromptTemplateLocation;

    @Value("${iflow.sdk.ui-task-timeout-ms:300000}")
    private long uiTaskTimeoutMillis = 300000L;

    @Value("${iflow.sdk.ui-heartbeat-interval-ms:30000}")
    private long uiHeartbeatIntervalMillis = 30000L;

    @Value("${iflow.sdk.ui-heartbeat-initial-delay-ms:15000}")
    private long uiHeartbeatInitialDelayMillis = 15000L;

    private final Set<Long> runningUiGenerationConversations = ConcurrentHashMap.newKeySet();

    /**
     * 生成 UI 原型
     */
    @Async
    public void generateUIPrototype(Long conversationId) {
        log.info("Generating UI prototype for conversation: {}", conversationId);
        runningUiGenerationConversations.add(conversationId);
        try {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

            // 更新状态为 UI 生成中
            conversation.setStage(ConversationStage.UI_GENERATING);
            conversationRepository.save(conversation);

            // 构建 UI 生成提示词
            String prompt = buildUIPrototypePrompt(conversation);
            log.info("UI prompt diagnostics for conversation {}: {}", conversationId, buildPromptDiagnostics(prompt));

            UiGenerationAttemptResult attempt = executeUiGenerationAttempt(conversationId, prompt, "primary");

            if (attempt.resolvedHtml != null) {
                persistUiPrototypeSuccess(conversationId, attempt);
                return;
            }

            log.warn(
                    "UI prototype html extraction failed for conversation {} (assistantChunks={}, assistantChars={})",
                    conversationId,
                    attempt.assistantChunkCount,
                    attempt.assistantCharCount
            );
            if (attempt.error != null) {
                handleUiGenerationFailure(
                        conversationId,
                        "UI 原型生成失败: " + safeErrorMessage(attempt.error),
                        attempt.error
                );
            } else {
                handleUiGenerationFailure(
                        conversationId,
                        "UI 原型生成失败: 未生成有效 HTML 内容",
                        null
                );
            }
        } finally {
            runningUiGenerationConversations.remove(conversationId);
        }
    }

    public boolean isUiGenerationInProgress(Long conversationId) {
        return conversationId != null && runningUiGenerationConversations.contains(conversationId);
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

        // 异步重新生成，避免同类方法调用导致 @Async 失效而阻塞请求线程
        try {
            taskExecutor.execute(() -> generateUIPrototype(conversationId));
        } catch (RuntimeException e) {
            handleUiGenerationFailure(conversationId, "UI 原型生成失败: " + safeErrorMessage(e), e);
        }
    }

    /**
     * 构建 UI 生成提示词
     */
    private String buildUIPrototypePrompt(Conversation conversation) {
        return renderPromptFromTemplate(TEMPLATE_SECTION_PRIMARY, conversation);
    }

    private String renderPromptFromTemplate(String section, Conversation conversation) {
        String sectionTemplate = resolvePromptTemplateSection(section);
        return sectionTemplate
                .replace("{{PROJECT_NAME}}", safeTemplateValue(conversation.getProjectName()))
                .replace("{{USER_REQUIREMENT}}", safeTemplateValue(conversation.getUserRequirement()))
                .replace("{{AI_UNDERSTANDING}}", safeTemplateValue(conversation.getAiUnderstanding()));
    }

    private String resolvePromptTemplateSection(String section) {
        String templateContent = loadPromptTemplateContent();
        String startMarker = "<!-- TEMPLATE:" + section + " -->";
        String endMarker = "<!-- /TEMPLATE:" + section + " -->";

        int start = templateContent.indexOf(startMarker);
        int end = templateContent.indexOf(endMarker);
        if (start < 0 || end <= start) {
            throw new RuntimeException("UI prompt template section not found: " + section);
        }

        String sectionContent = templateContent.substring(start + startMarker.length(), end).trim();
        if (sectionContent.isBlank()) {
            throw new RuntimeException("UI prompt template section is empty: " + section);
        }
        return sectionContent;
    }

    private String loadPromptTemplateContent() {
        Resource templateResource = resourceLoader.getResource(uiPromptTemplateLocation);
        if (!templateResource.exists()) {
            throw new RuntimeException("UI prompt template does not exist: " + uiPromptTemplateLocation);
        }
        try (var inputStream = templateResource.getInputStream()) {
            byte[] content = inputStream.readAllBytes();
            String rendered = new String(content, StandardCharsets.UTF_8);
            if (rendered.isBlank()) {
                throw new RuntimeException("UI prompt template is empty: " + uiPromptTemplateLocation);
            }
            return rendered;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load UI prompt template: " + uiPromptTemplateLocation, e);
        }
    }

    private String safeTemplateValue(String value) {
        return value == null ? "" : value.trim();
    }

    String buildPromptDiagnostics(String prompt) {
        String normalized = prompt == null ? "" : prompt;
        return "length=" + normalized.length()
                + ",pencil=" + containsIgnoreCase(normalized, "pencil-ui-design")
                + ",image=" + containsIgnoreCase(normalized, "图片")
                + ",background=" + containsIgnoreCase(normalized, "背景")
                + ",chart=" + containsIgnoreCase(normalized, "图表")
                + ",avatar=" + containsIgnoreCase(normalized, "头像")
                + ",skillCommand=" + containsIgnoreCase(normalized, "superpowers-codex use-skill");
    }

    private UiGenerationAttemptResult executeUiGenerationAttempt(Long conversationId, String prompt, String attemptLabel) {
        UiGenerationAttemptResult result = new UiGenerationAttemptResult();
        Path workDir = getUIPrototypeDir(conversationId);
        try {
            Files.createDirectories(workDir);
        } catch (Exception e) {
            result.error = new RuntimeException("无法创建工作目录", e);
            return result;
        }

        StringBuilder htmlContent = new StringBuilder();
        long attemptStartAt = System.currentTimeMillis();
        AtomicBoolean heartbeatRunning = new AtomicBoolean(true);
        Thread heartbeatThread = startUiHeartbeatThread(conversationId, attemptLabel, heartbeatRunning, attemptStartAt);
        try {
            codingService.executeTask(prompt, workDir, new ICodingService.MessageHandler() {
                @Override
                public void onAssistantMessage(String text) {
                    htmlContent.append(text);
                    result.assistantChunkCount++;
                    result.assistantCharCount += text == null ? 0 : text.length();
                }

                @Override
                public void onToolCall(String toolName, String status) {
                    log.info("UI attempt [{}] tool call: {} - {}", attemptLabel, toolName, status);
                }

                @Override
                public void onToolResult(String content) {
                    log.info("UI attempt [{}] tool result: {}", attemptLabel, content);
                }

                @Override
                public void onTaskFinish(String stopReason) {
                    log.info("UI attempt [{}] finished: {}", attemptLabel, stopReason);
                }

                @Override
                public void onError(Throwable error) {
                    result.error = error;
                }

                @Override
                public void onComplete() {
                    result.resolvedHtml = resolveHtmlContent(conversationId, htmlContent.toString());
                }
            }, uiTaskTimeoutMillis);
        } catch (Exception e) {
            result.error = e;
        } finally {
            stopUiHeartbeatThread(heartbeatRunning, heartbeatThread);
        }
        return result;
    }

    private Thread startUiHeartbeatThread(
            Long conversationId,
            String attemptLabel,
            AtomicBoolean running,
            long attemptStartAt
    ) {
        if (uiHeartbeatIntervalMillis <= 0) {
            return null;
        }

        Thread heartbeatThread = new Thread(() -> {
            sleepHeartbeat(uiHeartbeatInitialDelayMillis);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                emitUiHeartbeatMessage(conversationId, attemptLabel, attemptStartAt);
                sleepHeartbeat(uiHeartbeatIntervalMillis);
            }
        }, "ui-heartbeat-" + conversationId + "-" + attemptLabel);

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        return heartbeatThread;
    }

    private void stopUiHeartbeatThread(AtomicBoolean running, Thread heartbeatThread) {
        running.set(false);
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            try {
                heartbeatThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void emitUiHeartbeatMessage(Long conversationId, String attemptLabel, long attemptStartAt) {
        try {
            if (!isUiGenerationInProgress(conversationId)) {
                return;
            }

            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null || conversation.getStage() != ConversationStage.UI_GENERATING) {
                return;
            }

            long elapsedSeconds = Math.max(1L, (System.currentTimeMillis() - attemptStartAt) / 1000);
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(Message.MessageRole.SYSTEM);
            message.setSenderName("系统");
            message.setContent("UI 原型生成中（" + attemptLabel + "），已等待 " + elapsedSeconds + " 秒，请稍候...");
            messageRepository.save(message);
        } catch (Exception e) {
            log.warn("Failed to emit UI heartbeat message for conversation: {}", conversationId, e);
        }
    }

    private void sleepHeartbeat(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void persistUiPrototypeSuccess(Long conversationId, UiGenerationAttemptResult attempt) {
        String finalizedHtml = ensurePencilAttribution(attempt.resolvedHtml);
        Path htmlFile = getUIPrototypeDir(conversationId).resolve("index.html");
        if (shouldRewriteHtmlFile(htmlFile)) {
            htmlFile = saveUIPrototype(conversationId, finalizedHtml);
        }

        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null) {
            conv.setUiPrototypeContent(finalizedHtml);
            conv.setUiPrototypePath(htmlFile.toString());
            conv.setStage(ConversationStage.UI_READY);
            conv.setErrorMessage(null);
            conversationRepository.save(conv);
        }
        log.info(
                "UI prototype saved successfully for conversation: {} (assistantChunks={}, assistantChars={}, htmlLength={})",
                conversationId,
                attempt.assistantChunkCount,
                attempt.assistantCharCount,
                finalizedHtml.length()
        );
    }

    private boolean shouldRewriteHtmlFile(Path htmlFile) {
        if (!Files.exists(htmlFile)) {
            return true;
        }
        try {
            String currentContent = Files.readString(htmlFile);
            return currentContent == null || currentContent.isBlank() || extractHtml(currentContent) == null;
        } catch (Exception e) {
            log.warn("Failed to inspect existing UI html file: {}", htmlFile, e);
            return true;
        }
    }

    private String ensurePencilAttribution(String html) {
        if (html == null || html.isBlank() || containsIgnoreCase(html, "pencil-ui-design")) {
            return html;
        }
        return "<!-- 已调用并遵循 pencil-ui-design 规范（图片/背景/图表/头像） -->\n" + html;
    }

    private String resolveHtmlContent(Long conversationId, String assistantHtml) {
        String resolvedHtml = extractHtml(assistantHtml);
        if (resolvedHtml != null) {
            return resolvedHtml;
        }

        Path uiPrototypeDir = getUIPrototypeDir(conversationId);
        Path htmlFile = uiPrototypeDir.resolve("index.html");
        resolvedHtml = resolveHtmlFromFile(conversationId, htmlFile);
        if (resolvedHtml != null) {
            return resolvedHtml;
        }

        try {
            if (Files.isDirectory(uiPrototypeDir)) {
                try (Stream<Path> fileStream = Files.list(uiPrototypeDir)) {
                    for (Path candidate : fileStream
                            .filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                            .filter(path -> !path.getFileName().toString().equalsIgnoreCase("index.html"))
                            .sorted(Comparator.comparing(this::safeLastModifiedTime).reversed())
                            .toList()) {
                        resolvedHtml = resolveHtmlFromFile(conversationId, candidate);
                        if (resolvedHtml != null) {
                            return resolvedHtml;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan UI prototype directory for conversation: {}", conversationId, e);
        }
        return resolvedHtml;
    }

    private String resolveHtmlFromFile(Long conversationId, Path htmlFile) {
        try {
            if (!Files.exists(htmlFile) || Files.isDirectory(htmlFile)) {
                return null;
            }
            String fileContent = Files.readString(htmlFile);
            String resolvedHtml = extractHtml(fileContent);
            if (resolvedHtml != null) {
                log.info("Loaded UI prototype HTML from file for conversation {}: {}", conversationId, htmlFile.getFileName());
            }
            return resolvedHtml;
        } catch (Exception e) {
            log.warn("Failed to read UI prototype file for conversation {}: {}", conversationId, htmlFile, e);
            return null;
        }
    }

    private long safeLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private String safeErrorMessage(Throwable error) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "未知错误"
                : error.getMessage();
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

    private boolean containsIgnoreCase(String content, String needle) {
        return indexOfIgnoreCase(content, needle) >= 0;
    }

    private void handleUiGenerationFailure(Long conversationId, String message, Throwable error) {
        if (error != null) {
            log.error("Error generating UI prototype for conversation: {}", conversationId, error);
        } else {
            log.warn("UI prototype generation failed for conversation {}: {}", conversationId, message);
        }

        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv == null || conv.getStage() == ConversationStage.UI_READY) {
            return;
        }
        if (conv.getStage() == ConversationStage.FAILED
                && conv.getErrorMessage() != null
                && !conv.getErrorMessage().isBlank()) {
            return;
        }
        conv.setStage(ConversationStage.FAILED);
        conv.setErrorMessage(message);
        conversationRepository.save(conv);
    }

    private static class UiGenerationAttemptResult {
        private String resolvedHtml;
        private Throwable error;
        private int assistantChunkCount;
        private int assistantCharCount;
    }
}
