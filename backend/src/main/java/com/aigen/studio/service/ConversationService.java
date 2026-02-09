package com.aigen.studio.service;

import com.aigen.studio.dto.*;
import com.aigen.studio.entity.*;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话服务
 * 支持独立对话流程的实时交互
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PromptTaskService promptTaskService;
    private final CodeGenerationService codeGenerationService;
    private final UIPrototypeService uiPrototypeService;

    // ==================== 独立对话流程方法 ====================

    /**
     * 创建新的独立对话（空对话）
     */
    public ConversationDTO createNewConversation(String createdBy) {
        log.info("Creating new conversation by user: {}", createdBy);

        // 创建空对话，项目名称和需求将在后续交互中由 AI 理解后写入
        Conversation conversation = new Conversation();
        conversation.setProjectName("新对话");
        conversation.setUserRequirement(null);
        conversation.setCreatedBy(createdBy);
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);
        conversation.setUnderstandingConfirmed(false);

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
        conversation.setUserRequirement(message.getContent());
        conversation.setStage(ConversationStage.UNDERSTANDING);
        conversationRepository.save(conversation);

        try {
            // 调用 iFlow SDK 理解需求
            log.info("Understanding requirement for conversation: {}", conversation.getId());
            String aiUnderstanding = promptTaskService.understandRequirement(conversation.getUserRequirement());
            conversation.setAiUnderstanding(aiUnderstanding);
            conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
            conversationRepository.save(conversation);

            response.setContent("我已收到您的需求：" + message.getContent() + "\n\n" +
                    "让我理解一下您的需求：\n\n" +
                    conversation.getAiUnderstanding() + "\n\n" +
                    "请问这个理解是否正确？如果需要修改，请告诉我。");
        } catch (Exception e) {
            log.error("Failed to understand requirement", e);
            conversation.setStage(ConversationStage.UNDERSTANDING);
            conversation.setErrorMessage("理解需求失败: " + e.getMessage());
            conversationRepository.save(conversation);

            response.setContent("抱歉，理解需求时出现错误：" + e.getMessage() + "\n\n" +
                    "请稍后重试，或重新描述您的需求。");
        }
    }

    /**
     * 处理理解阶段
     */
    private void handleUnderstanding(Conversation conversation, MessageDTO message, MessageDTO response) {
        // 用户在补充需求
        String updatedRequirement = conversation.getUserRequirement() + "\n" + message.getContent();
        conversation.setUserRequirement(updatedRequirement);

        try {
            // 重新理解需求
            log.info("Re-understanding requirement for conversation: {}", conversation.getId());
            String aiUnderstanding = promptTaskService.understandRequirement(updatedRequirement);
            conversation.setAiUnderstanding(aiUnderstanding);
            conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
            conversationRepository.save(conversation);

            response.setContent("我已更新您的需求，让我重新理解：\n\n" +
                    conversation.getAiUnderstanding() + "\n\n" +
                    "请问这个理解是否正确？如果需要修改，请告诉我。");
        } catch (Exception e) {
            log.error("Failed to re-understand requirement", e);
            conversation.setErrorMessage("重新理解需求失败: " + e.getMessage());
            conversationRepository.save(conversation);

            response.setContent("抱歉，重新理解需求时出现错误：" + e.getMessage() + "\n\n" +
                    "请稍后重试。");
        }
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
            conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
            conversation.setUnderstandingConfirmed(false);
            conversation.setUiConfirmed(false);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation.setErrorMessage(null);
            conversationRepository.save(conversation);

            response.setContent("我已根据最新需求重新理解：\n\n" +
                    aiUnderstanding + "\n\n" +
                    "请问这个理解是否正确？如果需要修改，请继续告诉我。");
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
                || normalized.contains("需求调整")
                || normalized.contains("需求变更")
                || normalized.contains("补充需求")
                || normalized.contains("新增需求")
                || normalized.contains("需求补充")
                || normalized.contains("改成")
                || normalized.contains("改为")
                || normalized.contains("增加")
                || normalized.contains("新增")
                || normalized.contains("去掉")
                || normalized.contains("删除");
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
        dto.setGeneratedCodePath(conversation.getGeneratedCodePath());
        dto.setServiceStatus(conversation.getServiceStatus());
        dto.setPreviewUrl(conversation.getPreviewUrl());
        dto.setUiPrototypePath(conversation.getUiPrototypePath());
        dto.setUiPrototypeContent(conversation.getUiPrototypeContent());
        dto.setUiConfirmed(conversation.getUiConfirmed());
        dto.setErrorMessage(conversation.getErrorMessage());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());

        // 从消息表中加载消息列表
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<MessageDTO> messageDTOs = messages.stream()
                .map(msg -> {
                    MessageDTO msgDTO = new MessageDTO();
                    msgDTO.setId(msg.getId());
                    msgDTO.setRole(msg.getRole().name().toLowerCase());
                    msgDTO.setSenderName(msg.getSenderName());
                    msgDTO.setContent(msg.getContent());
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
