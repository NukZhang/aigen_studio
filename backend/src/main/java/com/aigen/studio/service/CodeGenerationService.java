package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PromptTaskService promptTaskService;
    private final PreviewScriptService previewScriptService;
    private final UIPrototypeService uiPrototypeService;
    private final FrontendScaffoldService frontendScaffoldService;
    private final BackendGenerationFixer backendGenerationFixer;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

    @Value("${iflow.sdk.code-heartbeat-interval-ms:30000}")
    private long codeHeartbeatIntervalMillis = 30000L;

    @Value("${iflow.sdk.code-heartbeat-initial-delay-ms:15000}")
    private long codeHeartbeatInitialDelayMillis = 15000L;

    private final Set<Long> runningCodeGenerationConversations = ConcurrentHashMap.newKeySet();

    private static final Pattern FRONTEND_API_CALL_PATTERN = Pattern.compile("\\baxios\\s*\\.|\\bfetch\\s*\\(|\\b\\w+\\s*\\.(get|post|put|delete|request)\\s*\\(");
    private static final Pattern FRONTEND_BACKEND_TARGET_PATTERN = Pattern.compile("['\"]\\/api(?:[/'\"?]|$)|baseURL\\s*:\\s*['\"][^'\"]+['\"]");
    private static final Pattern FRONTEND_API_IMPORT_PATTERN = Pattern.compile("from\\s+['\"][^'\"]*api[^'\"]*['\"]");
    private static final Pattern APP_ROUTER_VIEW_PATTERN = Pattern.compile("<\\s*(?:router-view|routerview)\\b", Pattern.CASE_INSENSITIVE);

    @Async
    public void generateCodeForConversationAsync(Long conversationId) {
        runningCodeGenerationConversations.add(conversationId);
        try {
            try {
                Conversation conversation = conversationRepository.findById(conversationId)
                        .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

                log.info("Starting code generation for conversation: {}", conversationId);

                Path outputPath = Paths.get(outputDir, "conversation-" + conversationId);
                Files.createDirectories(outputPath);

                conversation.setGeneratedCodePath(outputPath.toString());
                conversationRepository.save(conversation);

                sendProgressMessage(conversationId, "正在调用 iFlow SDK 生成代码，请稍候...", "system");

                String uiPrototypeHtml = uiPrototypeService.getUIPrototype(conversationId);
                String irContent = generateIRContent(conversation, uiPrototypeHtml);

                long generationStartAt = System.currentTimeMillis();
                AtomicLong lastProgressAt = new AtomicLong(generationStartAt);
                AtomicBoolean heartbeatRunning = new AtomicBoolean(true);
                Thread heartbeatThread = startCodeHeartbeatThread(
                        conversationId,
                        heartbeatRunning,
                        generationStartAt,
                        lastProgressAt
                );
                try {
                    promptTaskService.generateCode(irContent, outputPath, message -> {
                        lastProgressAt.set(System.currentTimeMillis());
                        log.info("Code generation log: {}", message);
                        sendProgressMessage(conversationId, message, "system");
                    });
                } finally {
                    stopCodeHeartbeatThread(heartbeatRunning, heartbeatThread);
                }

                backendGenerationFixer.fixGeneratedBackend(outputPath.resolve("backend"));

                Path frontendDir = outputPath.resolve("frontend");
                frontendScaffoldService.ensureVueScaffoldAndInjectPrototype(
                        frontendDir,
                        uiPrototypeHtml,
                        conversation.getProjectName()
                );
                verifyFrontendCallsBackend(frontendDir);

                ensurePreviewScripts(outputPath);

                conversation.setStage(ConversationStage.READY_TO_START);
                conversation.setServiceStatus("CODE_GENERATED");
                conversationRepository.save(conversation);

                sendProgressMessage(conversationId, "代码生成完成，请确认启动服务", "system");

                log.info("Code generation completed for conversation: {}", conversationId);
            } catch (Exception e) {
                log.error("Code generation failed for conversation: {}", conversationId, e);
                Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
                if (conversation != null) {
                    conversation.setStage(ConversationStage.FAILED);
                    conversation.setStatus(Conversation.ConversationStatus.FAILED);
                    conversation.setErrorMessage("代码生成失败: " + e.getMessage());
                    conversationRepository.save(conversation);

                    sendProgressMessage(conversationId, "代码生成失败: " + e.getMessage(), "system");
                }
            }
        } finally {
            runningCodeGenerationConversations.remove(conversationId);
        }
    }

    public boolean isCodeGenerationInProgress(Long conversationId) {
        return conversationId != null && runningCodeGenerationConversations.contains(conversationId);
    }

    private Thread startCodeHeartbeatThread(
            Long conversationId,
            AtomicBoolean running,
            long generationStartAt,
            AtomicLong lastProgressAt
    ) {
        if (codeHeartbeatIntervalMillis <= 0) {
            return null;
        }

        Thread heartbeatThread = new Thread(() -> {
            sleepHeartbeat(codeHeartbeatInitialDelayMillis);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                long lastProgress = lastProgressAt.get();
                if (now - lastProgress >= codeHeartbeatIntervalMillis) {
                    emitCodeHeartbeatMessage(conversationId, generationStartAt);
                    lastProgressAt.set(now);
                }
                sleepHeartbeat(Math.max(100L, codeHeartbeatIntervalMillis / 2));
            }
        }, "code-heartbeat-" + conversationId);

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        return heartbeatThread;
    }

    private void stopCodeHeartbeatThread(AtomicBoolean running, Thread heartbeatThread) {
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

    private void emitCodeHeartbeatMessage(Long conversationId, long generationStartAt) {
        try {
            if (!isCodeGenerationInProgress(conversationId)) {
                return;
            }

            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null || conversation.getStage() != ConversationStage.CODE_GENERATING) {
                return;
            }

            long elapsedSeconds = Math.max(1L, (System.currentTimeMillis() - generationStartAt) / 1000);
            sendProgressMessage(conversationId, "代码生成中，已等待 " + elapsedSeconds + " 秒，请稍候...", "system");
        } catch (Exception e) {
            log.warn("Failed to emit code generation heartbeat for conversation: {}", conversationId, e);
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

    void sendProgressMessage(Long conversationId, String content, String role) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return;
            }

            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(Message.MessageRole.valueOf(role.toUpperCase()));
            message.setSenderName("系统");
            message.setContent(content);
            messageRepository.save(message);

            log.info("Sent progress message for conversation {}: {}", conversationId, content);
        } catch (Exception e) {
            log.error("Failed to send progress message for conversation {}", conversationId, e);
        }
    }

    private void ensurePreviewScripts(Path outputPath) {
        Path frontendDir = outputPath.resolve("frontend");
        if (!Files.isDirectory(frontendDir)) {
            return;
        }
        previewScriptService.ensureFrontendStartScript(frontendDir);
        previewScriptService.ensureFrontendRouterBase(frontendDir);
    }

    private String generateIRContent(Conversation conversation, String uiPrototypeHtml) {
        StringBuilder ir = new StringBuilder();
        ir.append("{\n");
        ir.append("  \"projectName\": \"").append(escapeJson(conversation.getProjectName())).append("\",\n");
        ir.append("  \"userRequirement\": \"").append(escapeJson(conversation.getUserRequirement().replace("\n", " "))).append("\",\n");
        ir.append("  \"aiUnderstanding\": \"").append(escapeJson(conversation.getAiUnderstanding().replace("\n", " "))).append("\",\n");
        
        if (uiPrototypeHtml != null && !uiPrototypeHtml.trim().isEmpty()) {
            ir.append("  \"uiPrototypeHtml\": \"").append(escapeJson(uiPrototypeHtml.replace("\n", " ").replace("\"", "\\\""))).append("\",\n");
        }

        ir.append("  \"frontendBackendIntegration\": {\n");
        ir.append("    \"required\": true,\n");
        ir.append("    \"apiBasePath\": \"/api\",\n");
        ir.append("    \"notes\": \"frontend must call backend services using axios or fetch, and avoid pure mock-only pages\"\n");
        ir.append("  },\n");
        
        ir.append("  \"modules\": [\n");
        ir.append("    {\n");
        ir.append("      \"name\": \"frontend\",\n");
        ir.append("      \"type\": \"vue3\",\n");
        ir.append("      \"features\": []\n");
        ir.append("    },\n");
        ir.append("    {\n");
        ir.append("      \"name\": \"backend\",\n");
        ir.append("      \"type\": \"springboot\",\n");
        ir.append("      \"features\": []\n");
        ir.append("    }\n");
        ir.append("  ]\n");
        ir.append("}");
        
        return ir.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\b", "\\b")
                 .replace("\f", "\\f")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    private void verifyFrontendCallsBackend(Path frontendDir) {
        if (frontendDir == null || !Files.isDirectory(frontendDir)) {
            throw new RuntimeException("生成代码校验失败: 前端目录不存在，无法验证后端调用");
        }

        Path srcDir = frontendDir.resolve("src");
        if (!Files.isDirectory(srcDir)) {
            throw new RuntimeException("生成代码校验失败: 缺少 frontend/src，无法验证后端调用");
        }

        Path appVue = srcDir.resolve("App.vue");
        String appContent = readFileSilently(appVue);
        boolean appHasRouterView = APP_ROUTER_VIEW_PATTERN.matcher(appContent).find();
        boolean appHasBackendCallHint = FRONTEND_API_CALL_PATTERN.matcher(appContent).find()
                || FRONTEND_API_IMPORT_PATTERN.matcher(appContent).find();

        String mainContent = readFileSilently(resolveFirstExisting(srcDir, List.of("main.ts", "main.js")));
        boolean mainUsesRouter = mainContent.contains("use(router)") || mainContent.contains("createRouter(");

        if (mainUsesRouter && !appHasRouterView) {
            throw new RuntimeException("生成代码校验失败: main 入口使用了路由，但 App.vue 缺少 <router-view>，页面无法触达后端调用逻辑");
        }

        List<Path> sourceFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(srcDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isFrontendSourceFile(path.getFileName().toString()))
                    .forEach(sourceFiles::add);
        } catch (Exception e) {
            throw new RuntimeException("生成代码校验失败: 扫描前端源码失败", e);
        }

        boolean hasApiCall = false;
        boolean hasBackendTarget = false;
        boolean hasApiImport = false;
        for (Path sourceFile : sourceFiles) {
            String content = readFileSilently(sourceFile);
            if (!hasApiCall && FRONTEND_API_CALL_PATTERN.matcher(content).find()) {
                hasApiCall = true;
            }
            if (!hasBackendTarget && FRONTEND_BACKEND_TARGET_PATTERN.matcher(content).find()) {
                hasBackendTarget = true;
            }
            if (!hasApiImport && FRONTEND_API_IMPORT_PATTERN.matcher(content).find()) {
                hasApiImport = true;
            }
            if (hasApiCall && (hasBackendTarget || hasApiImport)) {
                break;
            }
        }

        if (!(hasApiCall && (hasBackendTarget || hasApiImport || appHasBackendCallHint))) {
            throw new RuntimeException("生成代码校验失败: 前端未检测到有效后端 API 调用，请重新生成代码");
        }

        log.info(
                "Frontend-backend integration verified: appHasRouterView={}, mainUsesRouter={}, hasApiCall={}, hasBackendTarget={}, hasApiImport={}",
                appHasRouterView, mainUsesRouter, hasApiCall, hasBackendTarget, hasApiImport
        );
    }

    private Path resolveFirstExisting(Path dir, List<String> candidates) {
        for (String candidate : candidates) {
            Path path = dir.resolve(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private boolean isFrontendSourceFile(String name) {
        return name.endsWith(".ts")
                || name.endsWith(".js")
                || name.endsWith(".vue")
                || name.endsWith(".tsx")
                || name.endsWith(".jsx");
    }

    private String readFileSilently(Path file) {
        if (file == null || !Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (Exception e) {
            log.warn("Failed to read file for frontend-backend verification: {}", file, e);
            return "";
        }
    }
}
