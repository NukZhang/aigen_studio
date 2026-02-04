package com.aigen.studio.service;

import com.aigen.studio.dto.*;
import com.aigen.studio.entity.*;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private final PreviewScriptService previewScriptService;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

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
            // 用户确认理解，进入代码生成阶段
            conversation.setUnderstandingConfirmed(true);
            conversation.setStage(ConversationStage.CODE_GENERATING);
            conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
            conversation = conversationRepository.save(conversation);

            log.info("User confirmed understanding for conversation: {}, moving to CODE_GENERATING", conversationId);

            // 异步调用 iFlow SDK 生成代码
            generateCodeForConversationAsync(conversationId);

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
     * 为对话生成代码（异步执行）
     */
    @Async
    private void generateCodeForConversationAsync(Long conversationId) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));

            log.info("Starting code generation for conversation: {}", conversationId);

            // 发送开始生成代码的进度消息
            sendProgressMessage(conversationId, "开始生成代码...", "system");

            // 创建输出目录
            Path outputPath = Paths.get(outputDir, "conversation-" + conversationId);
            Files.createDirectories(outputPath);

            conversation.setGeneratedCodePath(outputPath.toString());
            conversationRepository.save(conversation);

            // 发送正在生成代码的进度消息
            sendProgressMessage(conversationId, "正在调用 iFlow SDK 生成代码，请稍候...", "system");

            // 生成 IR 内容（简化版本）
            String irContent = generateIRContent(conversation);

            // 使用 PromptTaskService 生成代码
            promptTaskService.generateCode(irContent, outputPath, message -> {
                log.info("Code generation log: {}", message);
                // 保存进度消息到消息表
                sendProgressMessage(conversationId, message, "system");
            });

            ensurePreviewScripts(outputPath);

            // 更新对话状态
            conversation.setStage(ConversationStage.READY_TO_START);
            conversation.setServiceStatus("CODE_GENERATED");
            conversationRepository.save(conversation);

            // 发送代码生成完成的进度消息
            sendProgressMessage(conversationId, "代码生成完成，请确认启动服务", "system");

            log.info("Code generation completed for conversation: {}", conversationId);

            // TODO: 启动服务
            // startGeneratedService(conversation);

        } catch (Exception e) {
            log.error("Code generation failed for conversation: {}", conversationId, e);
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation != null) {
                conversation.setStage(ConversationStage.FAILED);
                conversation.setStatus(Conversation.ConversationStatus.FAILED);
                conversation.setErrorMessage("代码生成失败: " + e.getMessage());
                conversationRepository.save(conversation);
                
                // 发送失败消息
                sendProgressMessage(conversationId, "代码生成失败: " + e.getMessage(), "system");
            }
        }
    }

    /**
     * 发送进度消息到对话
     */
    private void sendProgressMessage(Long conversationId, String content, String role) {
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) return;

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

    /**
     * 生成 IR 内容（简化版本）
     */
    private String generateIRContent(Conversation conversation) {
        return String.format("""
            {
              "projectName": "%s",
              "userRequirement": "%s",
              "aiUnderstanding": "%s",
              "modules": [
                {
                  "name": "frontend",
                  "type": "vue3",
                  "features": []
                },
                {
                  "name": "backend",
                  "type": "springboot",
                  "features": []
                }
              ]
            }
            """,
            conversation.getProjectName(),
            conversation.getUserRequirement().replace("\n", " "),
            conversation.getAiUnderstanding().replace("\n", " ")
        );
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

        switch (conversation.getStage()) {
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

            case CODE_GENERATING:
                // 代码生成阶段，用户可以询问进度
                handleCodeGenerating(conversation, message, response);
                break;

            case READY_TO_START:
                response.setContent("代码生成完成，请点击“确认启动”以启动服务预览。");
                break;

            case SERVICE_STARTING:
                // 服务启动阶段，用户可以询问进度
                handleServiceStarting(conversation, message, response);
                break;

            case PREVIEWING:
                // 预览阶段，用户可以查看和测试
                handlePreviewing(conversation, message, response);
                break;

            default:
                response.setContent("当前阶段不支持消息交互。");
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
}
