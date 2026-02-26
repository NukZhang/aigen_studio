package com.aigen.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aigen.studio.dto.*;
import com.aigen.studio.entity.*;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话服务
 * 支持独立对话流程的实时交互
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final String DEFAULT_CONVERSATION_TITLE = "新对话";
    private static final int AUTO_TITLE_MAX_LENGTH = 18;
    private static final int MANUAL_TITLE_MAX_LENGTH = 50;
    private static final String AUTO_TITLE_ELLIPSIS = "…";
    private static final String REQUIREMENT_GATE_START = "<REQUIREMENT_GATE>";
    private static final String REQUIREMENT_GATE_END = "</REQUIREMENT_GATE>";
    private static final Pattern CLARIFICATION_PAYLOAD_PATTERN = Pattern.compile(
            "(?is)<CLARIFICATION_PAYLOAD>\\s*(.*?)\\s*</CLARIFICATION_PAYLOAD>"
    );
    private static final Pattern ANSWERED_QUESTION_PATTERN = Pattern.compile(
            "(?m)^\\s*澄清回答\\s*[：:]\\s*(.+?)\\s*$"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> TITLE_PREFIXES = List.of(
            "我想做一个", "我想做", "我想要一个", "我想要", "我要做一个", "我要做",
            "请帮我做一个", "请帮我", "帮我做一个", "帮我", "需要一个", "需要",
            "开发一个", "实现一个", "创建一个", "做一个"
    );

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PromptTaskService promptTaskService;
    private final CodeGenerationService codeGenerationService;
    private final UIPrototypeService uiPrototypeService;
    private final PreviewService previewService;
    private final SdacResourceService sdacResourceService;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Value("${iflow.sdk.understanding-heartbeat-interval-ms:15000}")
    private long understandingHeartbeatIntervalMillis = 15000L;

    @Value("${iflow.sdk.understanding-heartbeat-initial-delay-ms:5000}")
    private long understandingHeartbeatInitialDelayMillis = 5000L;

    @Value("${aigen.preview.allow-unverified-preview:false}")
    private boolean allowUnverifiedPreview = false;

    private final Set<Long> runningUnderstandingConversations = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedUnderstandingConversations = ConcurrentHashMap.newKeySet();

    private record ClarificationQuestionMeta(String id, String question) {}

    // ==================== 独立对话流程方法 ====================

    /**
     * 创建新的独立对话（空对话）
     */
    public ConversationDTO createNewConversation(String createdBy) {
        log.info("Creating new conversation by user: {}", createdBy);

        // 创建空对话，项目名称和需求将在后续交互中由 AI 理解后写入
        Conversation conversation = new Conversation();
        conversation.setProjectName(DEFAULT_CONVERSATION_TITLE);
        conversation.setUserRequirement(null);
        conversation.setCreatedBy(createdBy);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);
        conversation.setUnderstandingConfirmed(false);
        conversation.setUiConfirmed(false);
        conversation.setGateStatusJson(defaultGateStatusJson());

        conversation = conversationRepository.save(conversation);
        return convertToDTO(conversation);
    }

    /**
     * 获取独立对话详情
     */
    public ConversationDTO getNewConversationById(Long id) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
        return convertToDTO(conversation);
    }

    /**
     * 获取活跃的对话列表
     */
    public List<ConversationDTO> getActiveConversations(String createdBy) {
        List<Conversation> conversations = conversationRepository
                .findByStatusAndCreatedByOrderByCreatedAtDesc(
                        Conversation.ConversationStatus.ACTIVE,
                        createdBy
                );
        return conversations.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * 确认理解需求
     */
    public ConversationDTO confirmUnderstanding(Long conversationId, ConfirmUnderstandingRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        if (request.getConfirmed()) {
            // 用户确认理解，进入 UI 生成阶段（第3步）
            conversation.setUnderstandingConfirmed(true);
            conversation.setStage(ConversationStage.UI_GENERATING);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation = conversationRepository.save(conversation);

            log.info("User confirmed understanding for conversation: {}, moving to UI_GENERATING", conversationId);

            codeGenerationService.sendProgressMessage(conversationId, "正在生成 UI 原型...", "system");
            // 异步调用 iFlow SDK 生成 UI 原型
            uiPrototypeService.generateUIPrototype(conversationId);

        } else {
            // 用户不确认，返回到理解阶段
            conversation.setUnderstandingConfirmed(false);
            conversation.setStage(ConversationStage.UNDERSTANDING);
            log.info("User did not confirm understanding for conversation: {}, returning to UNDERSTANDING", conversationId);

            conversation = conversationRepository.save(conversation);
        }

        return convertToDTO(conversation);
    }

    /**
     * 手动更新对话标题
     */
    public ConversationDTO updateConversationTitle(Long conversationId, String projectName) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        String normalizedTitle = safeText(projectName);
        if (normalizedTitle.isBlank()) {
            throw new RuntimeException("Conversation title cannot be blank");
        }
        if (normalizedTitle.length() > MANUAL_TITLE_MAX_LENGTH) {
            throw new RuntimeException("Conversation title cannot exceed 50 characters");
        }

        conversation.setProjectName(normalizedTitle);
        conversation = conversationRepository.save(conversation);
        return convertToDTO(conversation);
    }


    /**
     * 发送消息到独立对话
     */
    public MessageDTO sendMessageToNewConversation(Long conversationId, MessageDTO message) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        log.info("Sending message to conversation {}: {}", conversationId, message.getContent());

        // 保存用户消息
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(Message.MessageRole.USER);
        userMessage.setSenderName("用户");
        userMessage.setContent(message.getContent());
        userMessage.setClarificationQuestionId(safeText(message.getClarificationQuestionId()));
        messageRepository.save(userMessage);

        // 根据当前阶段处理消息
        MessageDTO response = processMessageByStage(conversation, message);

        // 保存 AI 响应消息
        Message aiMessage = new Message();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole(Message.MessageRole.ASSISTANT);
        aiMessage.setSenderName("AI 开发者");
        aiMessage.setContent(response.getContent());
        messageRepository.save(aiMessage);

        return response;
    }

    /**
     * 根据当前阶段处理消息
     */
    private MessageDTO processMessageByStage(Conversation conversation, MessageDTO message) {
        MessageDTO response = new MessageDTO();
        response.setRole("assistant");
        response.setSenderName("AI 开发者");
        response.setTimestamp(LocalDateTime.now());
        response.setToolCalls(new ArrayList<>());

        if (tryHandleCrossStageCommand(conversation, message, response)) {
            return response;
        }

        ConversationStage stage = conversation.getStage();
        if (stage == null) {
            log.warn("Conversation {} stage is null, fallback to idle interaction mode", conversation.getId());
            handleIdleStageMessage(conversation, message, response);
            return response;
        }

        switch (stage) {
            case NEED_INPUT:
                // 需求输入阶段，用户提交需求
                handleNeedInput(conversation, message, response);
                break;

            case UNDERSTANDING:
                // 理解阶段，用户可以补充需求
                handleUnderstanding(conversation, message, response);
                break;

            case CLARIFYING:
                // 澄清阶段，用户可以补充关键信息
                handleUnderstanding(conversation, message, response);
                break;

            case UNDERSTANDING_CONFIRMED:
                // 确认阶段，用户确认理解结果
                handleUnderstandingConfirmed(conversation, message, response);
                break;

            case UI_GENERATING:
                handleUiGenerating(conversation, message, response);
                break;

            case CODE_GENERATING:
                // 代码生成阶段，用户可以询问进度
                handleCodeGenerating(conversation, message, response);
                break;

            case UI_READY:
            case UI_CONFIRMED:
            case READY_TO_START:
                handleIdleStageMessage(conversation, message, response);
                break;

            case SERVICE_STARTING:
                // 服务启动阶段，用户可以询问进度
                handleServiceStarting(conversation, message, response);
                break;

            case PREVIEWING:
                // 预览阶段，用户可以查看和测试
                handlePreviewing(conversation, message, response);
                break;

            case COMPLETED:
            case FAILED:
                handleIdleStageMessage(conversation, message, response);
                break;

            default:
                log.warn("Conversation {} stage {} is unsupported, fallback to idle interaction mode",
                        conversation.getId(), stage);
                handleIdleStageMessage(conversation, message, response);
                break;
        }

        return response;
    }

    /**
     * 处理需求输入阶段
     */
    private void handleNeedInput(Conversation conversation, MessageDTO message, MessageDTO response) {
        // 保存用户需求
        conversation.setUserRequirement(safeText(message.getContent()));
        conversation.setStage(ConversationStage.UNDERSTANDING);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setErrorMessage(null);
        conversationRepository.save(conversation);
        enqueueUnderstanding(conversation.getId());
        response.setContent("我已收到您的需求，正在后台理解中。\n\n" +
                "理解过程会持续输出进度与中间结论，请稍候。");
    }

    /**
     * 处理理解阶段
     */
    private void handleUnderstanding(Conversation conversation, MessageDTO message, MessageDTO response) {
        // 用户在补充需求
        String currentRequirement = safeText(conversation.getUserRequirement());
        String requirementDelta = safeText(message.getContent());
        String updatedRequirement = currentRequirement.isBlank()
                ? requirementDelta
                : currentRequirement + "\n" + requirementDelta;
        conversation.setUserRequirement(updatedRequirement);
        conversation.setStage(ConversationStage.UNDERSTANDING);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setErrorMessage(null);
        conversationRepository.save(conversation);

        enqueueUnderstanding(conversation.getId());
        response.setContent("已收到你的补充信息，正在后台重新理解需求。\n\n" +
                "你会看到持续的理解心跳和中间结果。");
    }

    /**
     * 处理理解确认阶段
     */
    private void handleUnderstandingConfirmed(Conversation conversation, MessageDTO message, MessageDTO response) {
        // 检查用户是否确认
        if (message.getContent().toLowerCase().contains("是") ||
            message.getContent().toLowerCase().contains("正确") ||
            message.getContent().toLowerCase().contains("可以") ||
            message.getContent().toLowerCase().contains("确认")) {

            // 用户确认，开始生成代码
            conversation.setStage(ConversationStage.CODE_GENERATING);
            conversationRepository.save(conversation);

            response.setContent("好的，我现在开始为您生成代码...\n\n" +
                    "代码生成过程可能需要几分钟时间，请稍候。");

            // TODO: 异步调用 iFlow SDK 生成代码
            // asyncGenerateCode(conversation);

        } else if (message.getContent().toLowerCase().contains("否") ||
                   message.getContent().toLowerCase().contains("不对") ||
                   message.getContent().toLowerCase().contains("错误")) {

            // 用户不确认，返回理解阶段
            conversation.setStage(ConversationStage.UNDERSTANDING);
            conversationRepository.save(conversation);

            response.setContent("好的，请告诉我哪里理解错误，我会重新理解您的需求。");

        } else {
            response.setContent("请确认理解是否正确（回答\"是\"或\"否\"），或告诉我需要修改的地方。");
        }
    }

    /**
     * 处理代码生成阶段
     */
    private void handleCodeGenerating(Conversation conversation, MessageDTO message, MessageDTO response) {
        response.setContent("代码正在生成中，请稍候...\n\n" +
                "您可以在\"代码编辑\"标签页查看实时生成的代码。");
    }

    private void handleUiGenerating(Conversation conversation, MessageDTO message, MessageDTO response) {
        if (isTaskRunning(conversation)) {
            response.setContent("UI 原型正在生成中，请稍候...\n\n" +
                    "任务完成后您可以输入“重新生成 UI”、“重新理解需求”或“重新生成代码”。");
            return;
        }
        handleIdleStageMessage(conversation, message, response);
    }

    /**
     * 处理服务启动阶段
     */
    private void handleServiceStarting(Conversation conversation, MessageDTO message, MessageDTO response) {
        response.setContent("服务正在启动中，请稍候...\n\n" +
                "服务启动完成后，您可以在\"预览\"标签页查看应用效果。");
    }

    /**
     * 处理预览阶段
     */
    private void handlePreviewing(Conversation conversation, MessageDTO message, MessageDTO response) {
        response.setContent("您可以在\"预览\"标签页查看应用效果。\n\n" +
                "如果需要修改，请告诉我。");
    }

    private void enqueueUnderstanding(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        if (runningUnderstandingConversations.add(conversationId)) {
            emitSystemMessage(conversationId, "需求理解任务已启动，正在分析中...");
            taskExecutor.execute(() -> runUnderstandingLoop(conversationId));
            return;
        }

        queuedUnderstandingConversations.add(conversationId);
        emitSystemMessage(conversationId, "已记录新的补充信息，当前理解结束后会自动继续。");
    }

    private void runUnderstandingLoop(Long conversationId) {
        try {
            while (true) {
                queuedUnderstandingConversations.remove(conversationId);
                runSingleUnderstanding(conversationId);
                if (!queuedUnderstandingConversations.remove(conversationId)) {
                    break;
                }
                emitSystemMessage(conversationId, "检测到最新补充信息，继续理解需求...");
            }
        } finally {
            runningUnderstandingConversations.remove(conversationId);
            if (queuedUnderstandingConversations.remove(conversationId)) {
                enqueueUnderstanding(conversationId);
            }
        }
    }

    private void runSingleUnderstanding(Long conversationId) {
        Conversation snapshotConversation = conversationRepository.findById(conversationId).orElse(null);
        if (snapshotConversation == null) {
            return;
        }

        String requirementSnapshot = safeText(snapshotConversation.getUserRequirement());
        if (requirementSnapshot.isBlank()) {
            return;
        }

        long startAt = System.currentTimeMillis();
        AtomicBoolean heartbeatRunning = new AtomicBoolean(true);
        AtomicReference<String> latestInsight = new AtomicReference<>("");
        AtomicBoolean firstChunkReceived = new AtomicBoolean(false);
        Thread heartbeatThread = startUnderstandingHeartbeatThread(
                conversationId,
                heartbeatRunning,
                startAt,
                latestInsight
        );

        String aiUnderstanding;
        try {
            aiUnderstanding = promptTaskService.understandRequirement(requirementSnapshot, chunk -> {
                String snippet = summarizeInsightSnippet(chunk);
                if (!snippet.isBlank()) {
                    latestInsight.set(snippet);
                }
                if (firstChunkReceived.compareAndSet(false, true)) {
                    emitSystemMessage(conversationId, "已收到初步理解结果，正在整理结构化内容...");
                }
            });
        } catch (Exception e) {
            stopUnderstandingHeartbeatThread(heartbeatRunning, heartbeatThread);
            handleUnderstandingFailure(conversationId, e);
            return;
        }

        stopUnderstandingHeartbeatThread(heartbeatRunning, heartbeatThread);

        Conversation latestConversation = conversationRepository.findById(conversationId).orElse(null);
        if (latestConversation == null) {
            return;
        }

        String latestRequirement = safeText(latestConversation.getUserRequirement());
        if (!latestRequirement.equals(requirementSnapshot)) {
            emitSystemMessage(conversationId, "检测到需求已更新，忽略本轮旧结果并继续处理最新内容。");
            queuedUnderstandingConversations.add(conversationId);
            return;
        }

        ConversationStage nextStage = resolveUnderstandingStage(aiUnderstanding);
        latestConversation.setAiUnderstanding(aiUnderstanding);
        latestConversation.setStage(nextStage);
        latestConversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        latestConversation.setErrorMessage(null);
        latestConversation.setUnderstandingConfirmed(false);
        latestConversation.setUiConfirmed(false);
        updateProjectNameAfterUnderstanding(latestConversation, requirementSnapshot);
        conversationRepository.save(latestConversation);

        persistAssistantMessage(conversationId, buildUnderstandingResponse(
                "我已根据当前信息完成一轮理解：",
                aiUnderstanding,
                nextStage,
                "请问这个理解是否正确？如果需要修改，请告诉我。"
        ));
    }

    private void handleUnderstandingFailure(Long conversationId, Exception e) {
        log.error("Failed to understand requirement asynchronously for conversation {}", conversationId, e);

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            conversation.setStage(ConversationStage.UNDERSTANDING);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation.setErrorMessage("重新理解需求失败: " + e.getMessage());
            conversationRepository.save(conversation);
        }

        persistAssistantMessage(conversationId, "抱歉，重新理解需求时出现错误：" + e.getMessage() + "\n\n请稍后重试。");
    }

    private Thread startUnderstandingHeartbeatThread(
            Long conversationId,
            AtomicBoolean running,
            long startAt,
            AtomicReference<String> latestInsight
    ) {
        if (understandingHeartbeatIntervalMillis <= 0) {
            return null;
        }

        Thread heartbeatThread = new Thread(() -> {
            sleepHeartbeat(understandingHeartbeatInitialDelayMillis);
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                emitUnderstandingHeartbeatMessage(conversationId, startAt, latestInsight.get());
                sleepHeartbeat(understandingHeartbeatIntervalMillis);
            }
        }, "understanding-heartbeat-" + conversationId);

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        return heartbeatThread;
    }

    private void stopUnderstandingHeartbeatThread(AtomicBoolean running, Thread heartbeatThread) {
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

    private void emitUnderstandingHeartbeatMessage(Long conversationId, long startAt, String latestInsight) {
        try {
            if (!runningUnderstandingConversations.contains(conversationId)) {
                return;
            }
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null || conversation.getStage() != ConversationStage.UNDERSTANDING) {
                return;
            }

            long elapsedSeconds = Math.max(1L, (System.currentTimeMillis() - startAt) / 1000);
            String insight = summarizeInsightSnippet(latestInsight);
            String content = "需求理解中，已等待 " + elapsedSeconds + " 秒，请稍候...";
            if (!insight.isBlank()) {
                content += "\n当前理解片段：" + insight;
            }
            emitSystemMessage(conversationId, content);
        } catch (Exception e) {
            log.warn("Failed to emit understanding heartbeat for conversation {}", conversationId, e);
        }
    }

    private String summarizeInsightSnippet(String text) {
        String normalized = safeText(text)
                .replaceAll("\\s+", " ")
                .replace("<REQUIREMENT_GATE>", "")
                .replace("</REQUIREMENT_GATE>", "")
                .replace("<CLARIFICATION_PAYLOAD>", "")
                .replace("</CLARIFICATION_PAYLOAD>", "");
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
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

    private void emitSystemMessage(Long conversationId, String content) {
        persistMessage(conversationId, Message.MessageRole.SYSTEM, "系统", content, null);
    }

    private void persistAssistantMessage(Long conversationId, String content) {
        persistMessage(conversationId, Message.MessageRole.ASSISTANT, "AI 开发者", content, null);
    }

    private void persistMessage(
            Long conversationId,
            Message.MessageRole role,
            String senderName,
            String content,
            String clarificationQuestionId
    ) {
        String normalizedContent = safeText(content);
        if (conversationId == null || normalizedContent.isBlank()) {
            return;
        }
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setSenderName(senderName);
        message.setContent(normalizedContent);
        message.setClarificationQuestionId(safeText(clarificationQuestionId));
        messageRepository.save(message);
    }

    private void handleIdleStageMessage(Conversation conversation, MessageDTO message, MessageDTO response) {
        if (isTaskRunning(conversation)) {
            response.setContent(buildTaskRunningMessage(conversation));
            return;
        }

        if (looksLikeRequirementRefinement(message.getContent())) {
            reUnderstandWithRequirementDelta(conversation, message.getContent(), response);
            return;
        }

        response.setContent("当前没有运行中的任务，您可以继续在对话框操作：\n\n" +
                "1. 输入“重新理解需求”\n" +
                "2. 输入“重新生成 UI”\n" +
                "3. 输入“重新生成代码”\n" +
                "4. 直接输入需求修改内容（我会重新理解需求）");
    }

    private boolean tryHandleCrossStageCommand(Conversation conversation, MessageDTO message, MessageDTO response) {
        String content = safeText(message.getContent());
        if (content.isBlank()) {
            response.setContent("请输入具体内容后再发送。");
            return true;
        }

        if (isRegenerateUiCommand(content)) {
            if (isTaskRunning(conversation)) {
                response.setContent(buildTaskRunningMessage(conversation));
                return true;
            }
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation.setErrorMessage(null);
            conversationRepository.save(conversation);
            uiPrototypeService.regenerateUIPrototype(conversation.getId());
            codeGenerationService.sendProgressMessage(conversation.getId(), "收到指令，正在重新生成 UI 原型...", "system");
            response.setContent("好的，正在重新生成 UI 原型，请稍候。");
            return true;
        }

        if (isRegenerateCodeCommand(content)) {
            if (isTaskRunning(conversation)) {
                response.setContent(buildTaskRunningMessage(conversation));
                return true;
            }
            conversation.setStage(ConversationStage.CODE_GENERATING);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation.setErrorMessage(null);
            conversationRepository.save(conversation);
            codeGenerationService.sendProgressMessage(conversation.getId(), "收到指令，正在重新生成代码...", "system");
            codeGenerationService.generateCodeForConversationAsync(conversation.getId());
            response.setContent("好的，正在重新生成代码，请稍候。");
            return true;
        }

        if (isReunderstandCommand(content)) {
            if (isTaskRunning(conversation)) {
                response.setContent(buildTaskRunningMessage(conversation));
                return true;
            }
            String requirementDelta = extractCommandPayload(content);
            if (!requirementDelta.isBlank()) {
                reUnderstandWithRequirementDelta(conversation, requirementDelta, response);
            } else {
                reUnderstandCurrentRequirement(conversation, response);
            }
            return true;
        }

        if (looksLikeRequirementRefinement(content)) {
            if (isTaskRunning(conversation)) {
                response.setContent(buildTaskRunningMessage(conversation));
                return true;
            }
            reUnderstandWithRequirementDelta(conversation, content, response);
            return true;
        }

        return false;
    }

    private void reUnderstandCurrentRequirement(Conversation conversation, MessageDTO response) {
        String currentRequirement = safeText(conversation.getUserRequirement());
        if (currentRequirement.isBlank()) {
            response.setContent("当前还没有可理解的需求内容，请先描述需求。");
            return;
        }
        reUnderstandRequirement(conversation, currentRequirement, response);
    }

    private void reUnderstandWithRequirementDelta(Conversation conversation, String requirementDelta, MessageDTO response) {
        String delta = safeText(requirementDelta);
        String updatedRequirement = safeText(conversation.getUserRequirement());
        if (!delta.isBlank()) {
            updatedRequirement = updatedRequirement.isBlank()
                    ? delta
                    : updatedRequirement + "\n" + delta;
        }
        if (updatedRequirement.isBlank()) {
            response.setContent("当前还没有可理解的需求内容，请先描述需求。");
            return;
        }

        conversation.setUserRequirement(updatedRequirement);
        reUnderstandRequirement(conversation, updatedRequirement, response);
    }

    private void reUnderstandRequirement(Conversation conversation, String requirement, MessageDTO response) {
        try {
            String aiUnderstanding = promptTaskService.understandRequirement(requirement);
            conversation.setAiUnderstanding(aiUnderstanding);
            ConversationStage nextStage = resolveUnderstandingStage(aiUnderstanding);
            conversation.setStage(nextStage);
            conversation.setUnderstandingConfirmed(false);
            conversation.setUiConfirmed(false);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation.setErrorMessage(null);
            updateProjectNameAfterUnderstanding(conversation, requirement);
            conversationRepository.save(conversation);

            response.setContent(buildUnderstandingResponse(
                    "我已根据最新需求重新理解：",
                    aiUnderstanding,
                    nextStage,
                    "请问这个理解是否正确？如果需要修改，请继续告诉我。"
            ));
        } catch (Exception e) {
            log.error("Failed to re-understand requirement in cross-stage command", e);
            conversation.setErrorMessage("重新理解需求失败: " + e.getMessage());
            conversationRepository.save(conversation);
            response.setContent("抱歉，重新理解需求时出现错误：" + e.getMessage() + "\n\n请稍后重试。");
        }
    }

    private boolean isTaskRunning(Conversation conversation) {
        if (conversation.getStatus() != Conversation.ConversationStatus.ACTIVE) {
            return false;
        }

        ConversationStage stage = conversation.getStage();
        if (stage == null) {
            return false;
        }

        if (stage == ConversationStage.CODE_GENERATING) {
            return codeGenerationService.isCodeGenerationInProgress(conversation.getId());
        }
        if (stage == ConversationStage.UNDERSTANDING) {
            return runningUnderstandingConversations.contains(conversation.getId());
        }
        if (stage == ConversationStage.SERVICE_STARTING) {
            return true;
        }
        if (stage == ConversationStage.UI_GENERATING) {
            return safeText(conversation.getUiPrototypeContent()).isBlank()
                    && uiPrototypeService.isUiGenerationInProgress(conversation.getId());
        }
        return false;
    }

    private String buildTaskRunningMessage(Conversation conversation) {
        return switch (conversation.getStage()) {
            case UNDERSTANDING -> "当前需求正在理解中，请等待理解任务完成后再操作。";
            case UI_GENERATING -> "当前 UI 原型正在生成中，请等待当前任务完成后再操作。";
            case CODE_GENERATING -> "当前代码任务正在运行，请等待完成后再操作。";
            case SERVICE_STARTING -> "当前服务正在启动，请等待完成后再操作。";
            default -> "当前任务正在运行，请稍候再试。";
        };
    }

    private boolean isRegenerateUiCommand(String content) {
        String normalized = normalize(content);
        return normalized.contains("重新生成ui")
                || normalized.contains("重生成ui")
                || normalized.contains("再生成ui")
                || normalized.contains("重新生成原型")
                || normalized.contains("重做ui")
                || normalized.contains("重新生成界面");
    }

    private boolean isRegenerateCodeCommand(String content) {
        String normalized = normalize(content);
        return normalized.contains("重新生成代码")
                || normalized.contains("重生成代码")
                || normalized.contains("再生成代码")
                || normalized.contains("重做代码")
                || normalized.contains("重新生成前端")
                || normalized.contains("重新生成后端");
    }

    private boolean isReunderstandCommand(String content) {
        String normalized = normalize(content);
        return normalized.contains("重新理解需求")
                || normalized.contains("重新理解")
                || normalized.contains("重理解需求")
                || normalized.contains("再理解需求")
                || normalized.contains("重新分析需求");
    }

    private boolean looksLikeRequirementRefinement(String content) {
        String normalized = normalize(content);
        return normalized.contains("需求修改")
                || normalized.contains("修改需求")
                || normalized.contains("需要修改")
                || normalized.contains("请修改")
                || normalized.contains("需求调整")
                || normalized.contains("需求变更")
                || normalized.contains("补充需求")
                || normalized.contains("新增需求")
                || normalized.contains("需求补充")
                || normalized.contains("修复")
                || normalized.contains("修正")
                || normalized.contains("报错")
                || normalized.contains("出错")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("失败")
                || normalized.contains("无法")
                || normalized.contains("不能")
                || normalized.contains("400")
                || normalized.contains("404")
                || normalized.contains("500")
                || normalized.contains("改成")
                || normalized.contains("改为")
                || normalized.contains("增加")
                || normalized.contains("新增")
                || normalized.contains("去掉")
                || normalized.contains("删除");
    }

    private ConversationStage resolveUnderstandingStage(String aiUnderstanding) {
        String nextAction = extractRequirementGateValue(aiUnderstanding, "NEXT_ACTION");
        if ("ASK_CLARIFICATION".equalsIgnoreCase(nextAction)) {
            return ConversationStage.CLARIFYING;
        }
        return ConversationStage.UNDERSTANDING_CONFIRMED;
    }

    private String extractRequirementGateValue(String aiUnderstanding, String key) {
        String content = safeText(aiUnderstanding);
        if (content.isBlank()) {
            return "";
        }

        int gateStart = content.indexOf(REQUIREMENT_GATE_START);
        int gateEnd = content.indexOf(REQUIREMENT_GATE_END);
        if (gateStart < 0 || gateEnd <= gateStart) {
            return "";
        }

        String gateSection = content.substring(gateStart + REQUIREMENT_GATE_START.length(), gateEnd).trim();
        String[] lines = gateSection.split("\\R");
        String prefix = key + ":";
        for (String line : lines) {
            String trimmed = safeText(line);
            if (trimmed.startsWith(prefix)) {
                return safeText(trimmed.substring(prefix.length()));
            }
        }
        return "";
    }

    private String buildUnderstandingResponse(String intro, String aiUnderstanding, ConversationStage stage, String confirmHint) {
        if (stage == ConversationStage.CLARIFYING) {
            return intro + "\n\n" +
                    "我还需要确认几个关键信息：\n\n" +
                    aiUnderstanding + "\n\n" +
                    "请按编号逐条补充，我会继续完善理解。";
        }

        return intro + "\n\n" +
                aiUnderstanding + "\n\n" +
                confirmHint;
    }

    private String extractCommandPayload(String content) {
        int separatorIndex = content.indexOf('：');
        if (separatorIndex < 0) {
            separatorIndex = content.indexOf(':');
        }
        if (separatorIndex < 0 || separatorIndex >= content.length() - 1) {
            return "";
        }
        return safeText(content.substring(separatorIndex + 1));
    }

    private String normalize(String text) {
        return safeText(text).toLowerCase().replaceAll("\\s+", "");
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private void updateProjectNameAfterUnderstanding(Conversation conversation, String requirement) {
        String currentTitle = safeText(conversation.getProjectName());
        if (!currentTitle.isBlank() && !DEFAULT_CONVERSATION_TITLE.equals(currentTitle)) {
            return;
        }

        String generatedTitle = generateShortProjectName(requirement);
        if (!generatedTitle.isBlank()) {
            conversation.setProjectName(generatedTitle);
        }
    }

    private String generateShortProjectName(String requirement) {
        String normalizedRequirement = safeText(requirement).replaceAll("\\s+", " ");
        if (normalizedRequirement.isBlank()) {
            return "";
        }

        String candidate = truncateByDelimiter(normalizedRequirement);
        for (String prefix : TITLE_PREFIXES) {
            if (candidate.startsWith(prefix)) {
                candidate = safeText(candidate.substring(prefix.length()));
                break;
            }
        }

        if (candidate.isBlank()) {
            candidate = truncateByDelimiter(normalizedRequirement);
        }

        if (candidate.length() > AUTO_TITLE_MAX_LENGTH) {
            candidate = safeText(candidate.substring(0, AUTO_TITLE_MAX_LENGTH)) + AUTO_TITLE_ELLIPSIS;
        }

        return candidate;
    }

    private String truncateByDelimiter(String text) {
        int delimiterIndex = findFirstDelimiterIndex(text);
        if (delimiterIndex > 0) {
            return safeText(text.substring(0, delimiterIndex));
        }
        return safeText(text);
    }

    private int findFirstDelimiterIndex(String text) {
        int index = -1;
        char[] delimiters = {'，', ',', '。', '！', '!', '？', '?', '：', ':', '；', ';', '\n', '\r'};
        for (char delimiter : delimiters) {
            int current = text.indexOf(delimiter);
            if (current >= 0 && (index == -1 || current < index)) {
                index = current;
            }
        }
        return index;
    }

    private List<ClarificationQuestionMeta> extractClarificationQuestions(String aiUnderstanding) {
        String source = safeText(aiUnderstanding);
        if (source.isBlank()) {
            return List.of();
        }

        Matcher matcher = CLARIFICATION_PAYLOAD_PATTERN.matcher(source);
        if (!matcher.find()) {
            return List.of();
        }

        String payload = safeText(matcher.group(1));
        if (payload.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray()) {
                return List.of();
            }

            List<ClarificationQuestionMeta> questions = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                JsonNode item = questionsNode.get(i);
                String question = safeText(item.path("question").asText(""));
                if (question.isBlank()) {
                    continue;
                }
                String id = safeText(item.path("id").asText(""));
                if (id.isBlank()) {
                    id = "q" + (i + 1);
                }
                questions.add(new ClarificationQuestionMeta(id, question));
            }
            return questions;
        } catch (Exception e) {
            log.warn("Failed to parse clarification payload, ignore progress extraction", e);
            return List.of();
        }
    }

    private List<String> resolveAnsweredQuestionIds(List<Message> messages, List<ClarificationQuestionMeta> clarificationQuestions) {
        if (clarificationQuestions.isEmpty() || messages.isEmpty()) {
            return List.of();
        }

        Map<String, String> questionIdByText = clarificationQuestions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        question -> normalizeQuestionForMatch(question.question()),
                        ClarificationQuestionMeta::id,
                        (left, right) -> left
                ));

        Set<String> answeredQuestionIds = new LinkedHashSet<>();
        for (Message message : messages) {
            if (message.getRole() != Message.MessageRole.USER) {
                continue;
            }

            String questionIdFromMessage = safeText(message.getClarificationQuestionId());
            if (!questionIdFromMessage.isBlank()) {
                for (ClarificationQuestionMeta clarificationQuestion : clarificationQuestions) {
                    if (clarificationQuestion.id().equals(questionIdFromMessage)) {
                        answeredQuestionIds.add(questionIdFromMessage);
                        break;
                    }
                }
                continue;
            }

            List<String> answeredQuestionTexts = extractAnsweredQuestionTexts(message.getContent());
            if (answeredQuestionTexts.isEmpty()) {
                continue;
            }

            for (String answeredQuestionText : answeredQuestionTexts) {
                String resolvedQuestionId = questionIdByText.get(normalizeQuestionForMatch(answeredQuestionText));
                if (resolvedQuestionId != null) {
                    answeredQuestionIds.add(resolvedQuestionId);
                }
            }
        }

        return List.copyOf(answeredQuestionIds);
    }

    private List<String> extractAnsweredQuestionTexts(String messageContent) {
        String content = safeText(messageContent);
        if (content.isBlank()) {
            return List.of();
        }

        Matcher matcher = ANSWERED_QUESTION_PATTERN.matcher(content);
        List<String> answeredQuestions = new ArrayList<>();
        while (matcher.find()) {
            String matchedQuestion = safeText(matcher.group(1));
            if (!matchedQuestion.isBlank()) {
                answeredQuestions.add(matchedQuestion);
            }
        }
        return answeredQuestions;
    }

    private String normalizeQuestionForMatch(String question) {
        String normalized = safeText(question)
                .replace("？", "?")
                .replace("：", ":")
                .toLowerCase()
                .replaceAll("^\\d+[\\.、\\)]\\s*", "")
                .replaceAll("\\s+", "");
        return safeText(normalized);
    }

    private Integer resolveCurrentQuestionIndex(List<ClarificationQuestionMeta> clarificationQuestions, List<String> answeredQuestionIds) {
        if (clarificationQuestions.isEmpty()) {
            return null;
        }

        Set<String> answeredSet = Set.copyOf(answeredQuestionIds);
        for (int i = 0; i < clarificationQuestions.size(); i++) {
            if (!answeredSet.contains(clarificationQuestions.get(i).id())) {
                return i;
            }
        }
        return null;
    }

    /**
     * 确认 UI 设计
     */
    public ConversationDTO confirmUIPrototype(Long conversationId) {
        log.info("Confirming UI prototype for conversation: {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        // 确认 UI 设计
        uiPrototypeService.confirmUIPrototype(conversationId);

        // 进入代码生成阶段（第4步）
        conversation.setStage(ConversationStage.CODE_GENERATING);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation = conversationRepository.save(conversation);

        log.info("UI confirmed for conversation: {}, moving to CODE_GENERATING", conversationId);

        codeGenerationService.sendProgressMessage(conversationId, "开始生成代码...", "system");
        // 异步调用 iFlow SDK 生成代码
        codeGenerationService.generateCodeForConversationAsync(conversationId);

        return convertToDTO(conversation);
    }

    // ==================== SDAC 流程 API ====================

    /**
     * understanding/parse：解析门控协议并产出澄清问题或契约卡
     */
    public Map<String, Object> parseUnderstanding(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        String requirement = safeText(conversation.getUserRequirement());
        if (requirement.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少需求内容，无法解析理解结果");
        }

        String aiUnderstanding = promptTaskService.understandRequirement(requirement);
        conversation.setAiUnderstanding(aiUnderstanding);
        conversation.setUnderstandingConfirmed(false);
        conversation.setMe2aiConfirmedAt(null);

        String nextAction = extractRequirementGateValue(aiUnderstanding, "NEXT_ACTION");
        if ("ASK_CLARIFICATION".equalsIgnoreCase(nextAction)) {
            List<Map<String, Object>> questions = extractClarificationQuestionsForApi(aiUnderstanding);
            conversation.setClarificationQuestionsJson(writeJson(Map.of("questions", questions)));
            conversation.setMe2aiContractJson(null);
            conversation.setStage(ConversationStage.CLARIFYING);
            updateGateStatus(conversation, "REQ", "BLOCKED");
            conversationRepository.save(conversation);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("nextAction", "ASK_CLARIFICATION");
            response.put("questions", questions);
            return response;
        }

        Map<String, Object> contract = buildMe2AiContract(aiUnderstanding, requirement);
        conversation.setClarificationQuestionsJson(null);
        conversation.setMe2aiContractJson(writeJson(contract));
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        updateGateStatus(conversation, "REQ", "READY_FOR_CONFIRM");
        conversationRepository.save(conversation);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nextAction", "READY_FOR_CONFIRM");
        response.put("contract", contract);
        return response;
    }

    /**
     * understanding/confirm：REQ Gate 确认
     */
    public ConversationDTO confirmUnderstandingSdac(Long conversationId, ConfirmUnderstandingRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        if (confirmed) {
            if (safeText(conversation.getMe2aiContractJson()).isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "REQ Gate 未通过：缺少需求契约卡");
            }
            conversation.setUnderstandingConfirmed(true);
            conversation.setMe2aiConfirmedAt(LocalDateTime.now());
            conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
            updateGateStatus(conversation, "REQ", "PASS");
        } else {
            conversation.setUnderstandingConfirmed(false);
            conversation.setStage(ConversationStage.UNDERSTANDING);
            updateGateStatus(conversation, "REQ", "BLOCKED");
        }

        conversation = conversationRepository.save(conversation);
        return convertToDTO(conversation);
    }

    /**
     * ui/design：REQ Gate 通过后进入 UI 设计
     */
    public Map<String, Object> designUi(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        if (!Boolean.TRUE.equals(conversation.getUnderstandingConfirmed())
                || safeText(conversation.getMe2aiContractJson()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "REQ Gate 未通过");
        }

        Map<String, Object> contract = readJsonMap(conversation.getMe2aiContractJson());
        Map<String, Object> uiSpec = buildUiSpec(contract, conversation);
        sdacResourceService.validateUiSpec(uiSpec);

        String prototypeHtml = safeText(conversation.getUiPrototypeContent());
        String prototypePath = safeText(conversation.getUiPrototypePath());
        if (prototypeHtml.isBlank()) {
            prototypeHtml = buildDefaultPrototypeHtml(conversation, uiSpec);
            Path htmlFile = uiPrototypeService.saveUIPrototype(conversationId, prototypeHtml);
            prototypePath = htmlFile.toString();
            conversation.setUiPrototypeContent(prototypeHtml);
            conversation.setUiPrototypePath(prototypePath);
        }

        conversation.setUiSpecJson(writeJson(uiSpec));
        conversation.setUiConfirmed(false);
        conversation.setUiConfirmedAt(null);
        conversation.setStage(ConversationStage.UI_DESIGNING);
        updateGateStatus(conversation, "UI", "READY_FOR_CONFIRM");
        conversation = conversationRepository.save(conversation);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversation", convertToDTO(conversation));
        response.put("uiSpec", uiSpec);
        response.put("prototypePath", safeText(conversation.getUiPrototypePath()));
        response.put("prototypeUrl", "/ui-prototype/" + conversationId);
        return response;
    }

    /**
     * ui/confirm：UI Gate 通过
     */
    public ConversationDTO confirmUiDesign(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        if (safeText(conversation.getUiSpecJson()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "UI Gate 未通过：缺少 UI_Spec");
        }

        conversation.setUiConfirmed(true);
        conversation.setUiConfirmedAt(LocalDateTime.now());
        conversation.setStage(ConversationStage.UI_CONFIRMED);
        updateGateStatus(conversation, "UI", "PASS");
        conversation = conversationRepository.save(conversation);
        return convertToDTO(conversation);
    }

    /**
     * implementation/plan：生成实现计划
     */
    public Map<String, Object> createImplementationPlan(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        if (!Boolean.TRUE.equals(conversation.getUiConfirmed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "UI Gate 未通过");
        }

        Map<String, Object> contract = readJsonMap(conversation.getMe2aiContractJson());
        List<String> scope = toStringList(contract.get("coreFeatures"));
        if (scope.isEmpty()) {
            scope = List.of("完成核心业务流程的最小可用实现");
        }

        List<String> nonGoals = toStringList(contract.get("scopeExclusions"));
        if (nonGoals.isEmpty()) {
            nonGoals = List.of("不扩展额外业务模块");
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("scope", scope.stream().limit(3).toList());
        plan.put("nonGoals", nonGoals.stream().limit(3).toList());
        plan.put("filesToChange", List.of(
                "frontend/src/views/Workspace.vue",
                "backend/src/main/java/com/aigen/studio/controller/ConversationController.java"
        ));
        plan.put("verifications", List.of(
                "cd backend && mvn test",
                "cd frontend && npm run test"
        ));
        plan.put("evidenceExpected", List.of(
                "测试报告摘要",
                "关键日志路径",
                "产物清单引用"
        ));
        sdacResourceService.validateImplementationPlan(plan);

        conversation.setImplementationPlanJson(writeJson(plan));
        conversation.setStage(ConversationStage.CODE_GENERATING);
        updateGateStatus(conversation, "IMP", "READY_FOR_VERIFY");
        conversationRepository.save(conversation);

        return plan;
    }

    /**
     * implementation/verify：验证并产出 Evidence Manifest（成功/失败都落盘）
     */
    public Map<String, Object> verifyImplementation(Long conversationId, ImplementationVerifyRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        if (safeText(conversation.getImplementationPlanJson()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IMP Gate 未通过：缺少 Implementation Plan");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        List<Map<String, Object>> verificationItems = toVerificationItems(request);
        String result = resolveVerificationResult(request, verificationItems);
        List<String> artifacts = new ArrayList<>(toArtifacts(request, conversation));
        Path stateUpdatePath = persistAi2AiStateUpdate(conversation, verificationItems, artifacts, result);
        artifacts.add(stateUpdatePath.toString());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("inputs", buildManifestInputs(conversation));
        manifest.put("verifications", verificationItems);
        manifest.put("result", result);
        manifest.put("artifacts", artifacts);
        manifest.put("timestamps", Map.of(
                "startedAt", startedAt.toString(),
                "finishedAt", LocalDateTime.now().toString()
        ));
        sdacResourceService.validateEvidenceManifest(manifest);

        Path manifestPath = persistEvidenceManifest(conversation, manifest);
        conversation.setEvidenceManifestPath(manifestPath.toString());
        updateGateStatus(conversation, "IMP", result);
        if ("PASS".equals(result)) {
            conversation.setStage(ConversationStage.READY_TO_START);
        }
        conversationRepository.save(conversation);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", result);
        response.put("manifestPath", manifestPath.toString());
        response.put("verifications", verificationItems);
        return response;
    }

    /**
     * preview/start：PREVIEW Gate（必须绑定 evidence）
     */
    public PreviewStatusDTO startPreviewWithEvidence(Long conversationId, PreviewStartRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        String evidenceRef = request == null ? "" : safeText(request.getEvidenceRef());
        if (evidenceRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须传 evidenceRef");
        }

        String storedEvidencePath = safeText(conversation.getEvidenceManifestPath());
        if (!allowUnverifiedPreview) {
            if (storedEvidencePath.isBlank()) {
                updateGateStatus(conversation, "PREVIEW", "BLOCKED");
                conversationRepository.save(conversation);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Preview Gate 未通过：缺少 evidence");
            }
            if (!isSameEvidenceRef(storedEvidencePath, evidenceRef)) {
                updateGateStatus(conversation, "PREVIEW", "BLOCKED");
                conversationRepository.save(conversation);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Preview Gate 未通过：evidenceRef 不匹配");
            }
        }

        updateGateStatus(conversation, "PREVIEW", "PASS");
        conversationRepository.save(conversation);

        PreviewStatusDTO status = previewService.startPreview(conversationId);
        Map<String, Object> previewContract = buildPreviewContract(status, evidenceRef);
        sdacResourceService.validatePreviewContract(previewContract);
        return status;
    }

    private List<Map<String, Object>> extractClarificationQuestionsForApi(String aiUnderstanding) {
        String source = safeText(aiUnderstanding);
        Matcher matcher = CLARIFICATION_PAYLOAD_PATTERN.matcher(source);
        if (!matcher.find()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(safeText(matcher.group(1)));
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> questions = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                JsonNode item = questionsNode.get(i);
                String question = safeText(item.path("question").asText(""));
                if (question.isBlank()) {
                    continue;
                }
                String id = safeText(item.path("id").asText(""));
                if (id.isBlank()) {
                    id = "q" + (i + 1);
                }
                List<String> options = new ArrayList<>();
                JsonNode optionsNode = item.path("options");
                if (optionsNode.isArray()) {
                    for (JsonNode option : optionsNode) {
                        String normalized = safeText(option.asText(""));
                        if (!normalized.isBlank()) {
                            options.add(normalized);
                        }
                    }
                }
                Map<String, Object> questionMap = new LinkedHashMap<>();
                questionMap.put("id", id);
                questionMap.put("question", question);
                questionMap.put("options", options);
                questions.add(questionMap);
            }
            return questions;
        } catch (Exception e) {
            log.warn("Failed to parse clarification payload for SDAC API", e);
            return List.of();
        }
    }

    private Map<String, Object> buildMe2AiContract(String aiUnderstanding, String requirementFallback) {
        List<String> lines = List.of(safeText(aiUnderstanding).split("\\R"));
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("projectGoal", new ArrayList<>());
        sections.put("platformAndTargetUsers", new ArrayList<>());
        sections.put("coreFeatures", new ArrayList<>());
        sections.put("keyBusinessRules", new ArrayList<>());
        sections.put("scopeExclusions", new ArrayList<>());
        sections.put("nonFunctionalRequirements", new ArrayList<>());
        sections.put("acceptanceCriteria", new ArrayList<>());
        sections.put("risksAndOpenQuestions", new ArrayList<>());

        String currentSection = "";
        for (String rawLine : lines) {
            String line = safeText(rawLine);
            if (line.isBlank() || line.startsWith("<REQUIREMENT_GATE>") || line.startsWith("</REQUIREMENT_GATE>")) {
                continue;
            }
            String section = resolveContractSection(line);
            if (!section.isBlank()) {
                currentSection = section;
                continue;
            }
            String normalized = line
                    .replaceFirst("^[-*]\\s*", "")
                    .replaceFirst("^\\d+[\\.、]\\s*", "")
                    .trim();
            if (normalized.isBlank() || currentSection.isBlank()) {
                continue;
            }
            sections.get(currentSection).add(normalized);
        }

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("projectGoal", firstOrDefault(sections.get("projectGoal"), safeText(requirementFallback)));
        contract.put("platformAndTargetUsers", firstOrDefault(sections.get("platformAndTargetUsers"), "平台和目标用户待确认"));
        contract.put("coreFeatures", nonEmptyOrDefault(sections.get("coreFeatures"), List.of("核心流程实现")));
        contract.put("keyBusinessRules", nonEmptyOrDefault(sections.get("keyBusinessRules"), List.of("关键规则待确认")));
        contract.put("scopeExclusions", nonEmptyOrDefault(sections.get("scopeExclusions"), List.of("范围外事项待补充")));
        contract.put("nonFunctionalRequirements", nonEmptyOrDefault(sections.get("nonFunctionalRequirements"), List.of("性能与安全基线")));
        contract.put("acceptanceCriteria", nonEmptyOrDefault(sections.get("acceptanceCriteria"), List.of("核心流程可验证")));
        contract.put("risksAndOpenQuestions", firstOrDefault(sections.get("risksAndOpenQuestions"), "无"));
        return contract;
    }

    private String resolveContractSection(String line) {
        String normalized = line.replace("#", "").replace("：", ":").trim();
        if (normalized.contains("项目目标")) {
            return "projectGoal";
        }
        if (normalized.contains("平台与目标用户")) {
            return "platformAndTargetUsers";
        }
        if (normalized.contains("核心功能")) {
            return "coreFeatures";
        }
        if (normalized.contains("关键业务规则")) {
            return "keyBusinessRules";
        }
        if (normalized.contains("范围外事项")) {
            return "scopeExclusions";
        }
        if (normalized.contains("非功能要求")) {
            return "nonFunctionalRequirements";
        }
        if (normalized.contains("验收标准")) {
            return "acceptanceCriteria";
        }
        if (normalized.contains("风险与待确认项")) {
            return "risksAndOpenQuestions";
        }
        return "";
    }

    private Map<String, Object> buildUiSpec(Map<String, Object> contract, Conversation conversation) {
        List<String> coreFeatures = toStringList(contract.get("coreFeatures"));
        String projectName = safeText(conversation.getProjectName());
        if (projectName.isBlank()) {
            projectName = "应用";
        }

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("id", "home");
        page.put("name", projectName + "首页");
        page.put("purpose", "承载核心业务入口");
        page.put("components", nonEmptyOrDefault(coreFeatures, List.of("导航栏", "主内容区")));
        page.put("states", List.of("loading", "success"));
        page.put("emptyAndErrorStates", List.of("empty", "error"));
        page.put("permissions", List.of("public"));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("path", "/");
        route.put("pageId", "home");

        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("from", "home");
        interaction.put("action", "click_primary_action");
        interaction.put("to", "home");
        interaction.put("notes", "主流程占位交互");

        Map<String, Object> uiSpec = new LinkedHashMap<>();
        uiSpec.put("pages", List.of(page));
        uiSpec.put("routes", List.of(route));
        uiSpec.put("globalStates", List.of("auth"));
        uiSpec.put("interactions", List.of(interaction));
        return uiSpec;
    }

    private String buildDefaultPrototypeHtml(Conversation conversation, Map<String, Object> uiSpec) {
        String title = safeText(conversation.getProjectName());
        if (title.isBlank()) {
            title = "UI Prototype";
        }
        List<String> features = toStringList(uiSpec.get("globalStates"));
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #1f2937; }
                    .wrap { max-width: 920px; margin: 40px auto; padding: 24px; }
                    .card { background: #fff; border-radius: 16px; padding: 24px; box-shadow: 0 8px 28px rgba(0,0,0,0.08); }
                    h1 { margin: 0 0 12px; font-size: 24px; }
                    p { margin: 0 0 16px; line-height: 1.6; }
                    ul { margin: 0; padding-left: 20px; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <h1>%s</h1>
                      <p>UI 设计阶段原型（可用于确认主流程）。</p>
                      <ul><li>%s</li></ul>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(title, title, String.join("</li><li>", nonEmptyOrDefault(features, List.of("状态管理"))));
    }

    private List<Map<String, Object>> toVerificationItems(ImplementationVerifyRequest request) {
        if (request == null || request.getVerifications() == null || request.getVerifications().isEmpty()) {
            return List.of(Map.of(
                    "cmd", "manual-check",
                    "status", "FAIL",
                    "summary", "未提供验证项",
                    "logsRef", ""
            ));
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ImplementationVerifyRequest.VerificationItem item : request.getVerifications()) {
            String status = safeText(item.getStatus()).toUpperCase();
            if (!"PASS".equals(status)) {
                status = "FAIL";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cmd", safeText(item.getCmd()).isBlank() ? "unknown" : safeText(item.getCmd()));
            row.put("status", status);
            row.put("summary", safeText(item.getSummary()));
            row.put("logsRef", safeText(item.getLogsRef()));
            items.add(row);
        }
        return items;
    }

    private String resolveVerificationResult(ImplementationVerifyRequest request, List<Map<String, Object>> verificationItems) {
        if (request != null && request.getPassed() != null) {
            return request.getPassed() ? "PASS" : "FAIL";
        }
        for (Map<String, Object> item : verificationItems) {
            if (!"PASS".equals(item.get("status"))) {
                return "FAIL";
            }
        }
        return "PASS";
    }

    private List<String> toArtifacts(ImplementationVerifyRequest request, Conversation conversation) {
        if (request != null && request.getArtifacts() != null && !request.getArtifacts().isEmpty()) {
            return request.getArtifacts().stream().map(this::safeText).filter(s -> !s.isBlank()).toList();
        }
        String codePath = safeText(conversation.getGeneratedCodePath());
        if (!codePath.isBlank()) {
            return List.of(codePath);
        }
        return List.of("n/a");
    }

    private Map<String, Object> buildManifestInputs(Conversation conversation) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("me2aiContractHash", hashText(conversation.getMe2aiContractJson()));
        inputs.put("uiSpecHash", hashText(conversation.getUiSpecJson()));
        inputs.put("planHash", hashText(conversation.getImplementationPlanJson()));
        String irHash = hashText(conversation.getAiUnderstanding());
        if (!irHash.isBlank()) {
            inputs.put("irHash", irHash);
        }
        return inputs;
    }

    private Path persistAi2AiStateUpdate(Conversation conversation,
                                         List<Map<String, Object>> verificationItems,
                                         List<String> artifacts,
                                         String result) {
        Path root = resolveConversationRoot(conversation);
        Path evidenceDir = root.resolve("evidence");
        try {
            Files.createDirectories(evidenceDir);
            List<String> commands = verificationItems.stream()
                    .map(item -> safeText(Objects.toString(item.get("cmd"), "")))
                    .filter(cmd -> !cmd.isBlank())
                    .toList();
            List<String> failedSummaries = verificationItems.stream()
                    .filter(item -> "FAIL".equalsIgnoreCase(safeText(Objects.toString(item.get("status"), ""))))
                    .map(item -> safeText(Objects.toString(item.get("summary"), "")))
                    .filter(summary -> !summary.isBlank())
                    .toList();

            String markdown = sdacResourceService.renderAi2AiStateUpdate(
                    "完成 implementation/verify，更新证据链并刷新 Gate 状态",
                    "conversationId=%d, result=%s".formatted(conversation.getId(), result),
                    commands,
                    result,
                    artifacts,
                    failedSummaries
            );
            String fileName = "ai2ai-state-update-" + System.currentTimeMillis() + ".md";
            Path stateFile = evidenceDir.resolve(fileName);
            Files.writeString(stateFile, markdown, StandardCharsets.UTF_8);
            return stateFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist AI2AI state update", e);
        }
    }

    private Path persistEvidenceManifest(Conversation conversation, Map<String, Object> manifest) {
        Path root = resolveConversationRoot(conversation);
        Path evidenceDir = root.resolve("evidence");
        try {
            Files.createDirectories(evidenceDir);
            String fileName = "manifest-" + System.currentTimeMillis() + ".json";
            Path manifestFile = evidenceDir.resolve(fileName);
            Files.writeString(manifestFile, writeJson(manifest), StandardCharsets.UTF_8);
            return manifestFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist Evidence Manifest", e);
        }
    }

    private Path resolveConversationRoot(Conversation conversation) {
        String generatedCodePath = safeText(conversation.getGeneratedCodePath());
        if (!generatedCodePath.isBlank()) {
            return Paths.get(generatedCodePath).toAbsolutePath().normalize();
        }
        Path fallback = Paths.get("generated-code", "conversation-" + conversation.getId()).toAbsolutePath().normalize();
        conversation.setGeneratedCodePath(fallback.toString());
        return fallback;
    }

    private boolean isSameEvidenceRef(String persistedPath, String requestedRef) {
        String persisted = safeText(persistedPath);
        String requested = safeText(requestedRef);
        if (persisted.equals(requested)) {
            return true;
        }
        if (persisted.endsWith(requested) || requested.endsWith(persisted)) {
            return true;
        }
        try {
            Path p1 = Paths.get(persisted).normalize();
            Path p2 = Paths.get(requested).normalize();
            if (p1.equals(p2)) {
                return true;
            }
            return Objects.equals(p1.getFileName(), p2.getFileName());
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> buildPreviewContract(PreviewStatusDTO status, String evidenceRef) {
        String previewUrl = safeText(status == null ? "" : status.getFrontendUrl());
        if (previewUrl.isBlank()) {
            previewUrl = safeText(status == null ? "" : status.getBackendUrl());
        }
        if (previewUrl.isBlank()) {
            previewUrl = "N/A";
        }

        List<Integer> ports = new ArrayList<>();
        if (status != null && status.getFrontendPort() != null) {
            ports.add(status.getFrontendPort());
        }
        if (status != null && status.getBackendPort() != null && !ports.contains(status.getBackendPort())) {
            ports.add(status.getBackendPort());
        }
        if (ports.isEmpty()) {
            ports.add(0);
        }

        List<String> knownLimits = new ArrayList<>();
        String message = safeText(status == null ? "" : status.getMessage());
        if (!message.isBlank()) {
            knownLimits.add(message);
        } else {
            knownLimits.add("无");
        }

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("previewUrl", previewUrl);
        contract.put("ports", ports);
        contract.put("startSteps", List.of(
                "调用 /conversations/{id}/preview/start 并传 evidenceRef",
                "等待 preview 状态进入 running=true"
        ));
        contract.put("evidenceRef", safeText(evidenceRef));
        contract.put("knownLimits", knownLimits);
        contract.put("testAccounts", List.of());
        return contract;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> safeText(item == null ? "" : item.toString()))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> nonEmptyOrDefault(List<String> source, List<String> fallback) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        return source;
    }

    private String firstOrDefault(List<String> source, String fallback) {
        if (source != null) {
            for (String item : source) {
                String normalized = safeText(item);
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
        }
        return safeText(fallback).isBlank() ? "待补充" : safeText(fallback);
    }

    private Map<String, Object> readJsonMap(String json) {
        String source = safeText(json);
        if (source.isBlank()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(source, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse json map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write json", e);
        }
    }

    private String hashText(String content) {
        String source = safeText(content);
        if (source.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> parseGateStatusJson(String rawJson) {
        Map<String, String> loadedDefaults = sdacResourceService == null ? null : sdacResourceService.loadDefaultGateStatuses();
        Map<String, String> defaultMap = loadedDefaults == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loadedDefaults);
        if (defaultMap.isEmpty()) {
            defaultMap.put("REQ", "PENDING");
            defaultMap.put("UI", "PENDING");
            defaultMap.put("IMP", "PENDING");
            defaultMap.put("PREVIEW", "PENDING");
        }

        String source = safeText(rawJson);
        if (source.isBlank()) {
            return defaultMap;
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(source, new TypeReference<Map<String, Object>>() {});
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String key = safeText(entry.getKey()).toUpperCase();
                if (defaultMap.containsKey(key) && entry.getValue() != null) {
                    defaultMap.put(key, safeText(entry.getValue().toString()).toUpperCase());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse gate status json, fallback to default map");
        }
        return defaultMap;
    }

    private void updateGateStatus(Conversation conversation, String gate, String status) {
        if (conversation == null) {
            return;
        }
        Map<String, String> gateStatusMap = parseGateStatusJson(conversation.getGateStatusJson());
        gateStatusMap.put(safeText(gate).toUpperCase(), safeText(status).toUpperCase());
        conversation.setGateStatusJson(writeJson(gateStatusMap));
    }

    private String defaultGateStatusJson() {
        return writeJson(parseGateStatusJson(""));
    }

    /**
     * 将 Conversation 实体转换为 DTO
     */
    private ConversationDTO convertToDTO(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setProjectName(conversation.getProjectName());
        dto.setStatus(conversation.getStatus().name());
        dto.setStage(conversation.getStage().name());
        dto.setUserRequirement(conversation.getUserRequirement());
        dto.setAiUnderstanding(conversation.getAiUnderstanding());
        dto.setUnderstandingConfirmed(conversation.getUnderstandingConfirmed());
        dto.setMe2aiContractJson(conversation.getMe2aiContractJson());
        dto.setMe2aiConfirmedAt(conversation.getMe2aiConfirmedAt());
        dto.setClarificationQuestionsJson(conversation.getClarificationQuestionsJson());
        dto.setGeneratedCodePath(conversation.getGeneratedCodePath());
        dto.setServiceStatus(conversation.getServiceStatus());
        dto.setPreviewUrl(conversation.getPreviewUrl());
        dto.setUiPrototypePath(conversation.getUiPrototypePath());
        dto.setUiPrototypeContent(conversation.getUiPrototypeContent());
        dto.setUiSpecJson(conversation.getUiSpecJson());
        dto.setUiConfirmed(conversation.getUiConfirmed());
        dto.setUiConfirmedAt(conversation.getUiConfirmedAt());
        dto.setImplementationPlanJson(conversation.getImplementationPlanJson());
        dto.setEvidenceManifestPath(conversation.getEvidenceManifestPath());
        dto.setGateStatusJson(conversation.getGateStatusJson());
        dto.setErrorMessage(conversation.getErrorMessage());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());

        // 从消息表中加载消息列表
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<ClarificationQuestionMeta> clarificationQuestions = extractClarificationQuestions(conversation.getAiUnderstanding());
        List<String> answeredQuestionIds = resolveAnsweredQuestionIds(messages, clarificationQuestions);
        dto.setAnsweredQuestionIds(answeredQuestionIds);
        dto.setCurrentQuestionIndex(resolveCurrentQuestionIndex(clarificationQuestions, answeredQuestionIds));

        List<MessageDTO> messageDTOs = messages.stream()
                .map(msg -> {
                    MessageDTO msgDTO = new MessageDTO();
                    msgDTO.setId(msg.getId());
                    msgDTO.setRole(msg.getRole().name().toLowerCase());
                    msgDTO.setSenderName(msg.getSenderName());
                    msgDTO.setContent(msg.getContent());
                    msgDTO.setClarificationQuestionId(msg.getClarificationQuestionId());
                    msgDTO.setTimestamp(msg.getCreatedAt());
                    msgDTO.setToolCalls(new ArrayList<>());
                    return msgDTO;
                })
                .toList();
        dto.setMessages(messageDTOs);

        return dto;
    }

    /**
     * 修复 conversation 状态（用于数据修复）
     */
    public ConversationDTO fixConversationStage(Long conversationId, String stage) {
        log.info("Fixing conversation {} stage to: {}", conversationId, stage);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

        try {
            // 转换阶段枚举
            ConversationStage newStage = ConversationStage.valueOf(stage);
            conversation.setStage(newStage);

            // 如果是 UI_CONFIRMED 阶段，设置 ui_confirmed 为 true
            if (newStage == ConversationStage.UI_CONFIRMED) {
                conversation.setUiConfirmed(true);
                // 清除错误信息
                conversation.setErrorMessage(null);
            }

            // 如果是从 FAILED 状态修复，也清除错误信息
            if (conversation.getStatus() == Conversation.ConversationStatus.FAILED) {
                conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
                conversation.setErrorMessage(null);
            }

            conversation = conversationRepository.save(conversation);
            log.info("Successfully fixed conversation {} stage to {}", conversationId, stage);

            return convertToDTO(conversation);
        } catch (IllegalArgumentException e) {
            log.error("Invalid stage: {}", stage, e);
            throw new RuntimeException("Invalid stage: " + stage + ". Valid stages are: " +
                    java.util.Arrays.stream(ConversationStage.values())
                            .map(Enum::name)
                            .reduce((a, b) -> a + ", " + b));
        }
    }
}
