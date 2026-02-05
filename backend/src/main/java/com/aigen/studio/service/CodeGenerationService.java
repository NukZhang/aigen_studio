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

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PromptTaskService promptTaskService;
    private final PreviewScriptService previewScriptService;
    private final UIPrototypeService uiPrototypeService;

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

    @Async
    public void generateCodeForConversationAsync(Long conversationId) {
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

            promptTaskService.generateCode(irContent, outputPath, message -> {
                log.info("Code generation log: {}", message);
                sendProgressMessage(conversationId, message, "system");
            });

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
}
