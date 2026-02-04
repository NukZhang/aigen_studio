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

            String irContent = generateIRContent(conversation);

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
}
