package com.aigen.studio.service;

import com.aigen.studio.dto.ConfirmUnderstandingRequest;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-ready-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConversationReadyStageTest {

    @TempDir
    static Path outputDir;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        PromptTaskService promptTaskService() {
            return new PromptTaskService(null) {
                @Override
                public void generateCode(String irContent, Path outputPath, Consumer<String> logConsumer) {
                    logConsumer.accept("generated");
                }

                @Override
                public String understandRequirement(String userRequirement) {
                    return "understood";
                }
            };
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("iflow.sdk.output-dir", () -> outputDir.toString());
    }

    @Test
    void confirmUnderstandingMarksReadyToStartAndSendsMessage() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(false);
        conversation = conversationRepository.save(conversation);

        conversationService.confirmUnderstanding(conversation.getId(), new ConfirmUnderstandingRequest(true, null));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.READY_TO_START, updated.getStage());

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertTrue(messages.stream().anyMatch(msg -> msg.getContent().contains("代码生成完成，请确认启动服务")));
    }

    @Test
    void confirmUnderstandingUsesConfiguredOutputDir() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(false);
        conversation = conversationRepository.save(conversation);

        conversationService.confirmUnderstanding(conversation.getId(), new ConfirmUnderstandingRequest(true, null));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertTrue(updated.getGeneratedCodePath().startsWith(outputDir.toString()));
    }
}
