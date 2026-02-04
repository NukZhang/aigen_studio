package com.aigen.studio.service;

import com.aigen.studio.dto.ConfirmUnderstandingRequest;
import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static CountDownLatch generationStarted;
    private static CountDownLatch allowComplete;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void resetLatches() {
        generationStarted = new CountDownLatch(1);
        allowComplete = new CountDownLatch(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        PromptTaskService promptTaskService() {
            return new PromptTaskService(null) {
                @Override
                public void generateCode(String irContent, Path outputPath, Consumer<String> logConsumer) {
                    generationStarted.countDown();
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
    void confirmUnderstandingReturnsBeforeGenerationCompletes() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(false);
        conversation = conversationRepository.save(conversation);
        final Long conversationId = conversation.getId();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ConversationDTO> future = executor.submit(() ->
                conversationService.confirmUnderstanding(conversationId, new ConfirmUnderstandingRequest(true, null))
        );

        try {
            ConversationDTO response = future.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(response);
        } finally {
            allowComplete.countDown();
            executor.shutdownNow();
        }

        Conversation updated = conversationRepository.findById(conversationId).orElseThrow();
        assertEquals(ConversationStage.CODE_GENERATING, updated.getStage());

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        assertTrue(messages.stream().anyMatch(msg -> msg.getContent().contains("开始生成代码")));
    }

    @Test
    void confirmUnderstandingEventuallyMarksReadyToStart() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation.setUnderstandingConfirmed(false);
        conversation = conversationRepository.save(conversation);
        final Long conversationId = conversation.getId();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ConversationDTO> future = executor.submit(() ->
                conversationService.confirmUnderstanding(conversationId, new ConfirmUnderstandingRequest(true, null))
        );

        try {
            future.get(500, TimeUnit.MILLISECONDS);
            assertTrue(generationStarted.await(2, TimeUnit.SECONDS));
        } finally {
            allowComplete.countDown();
            executor.shutdownNow();
        }

        Conversation updated = waitForStage(conversationId, ConversationStage.READY_TO_START, 5);
        assertTrue(updated.getGeneratedCodePath().startsWith(outputDir.toString()));
    }

    private Conversation waitForStage(Long conversationId, ConversationStage stage, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Conversation conversation;
        do {
            conversation = conversationRepository.findById(conversationId).orElseThrow();
            if (conversation.getStage() == stage) {
                return conversation;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return conversation;
    }
}
