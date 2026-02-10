package com.aigen.studio.service;

import com.aigen.studio.dto.ConfirmUnderstandingRequest;
import com.aigen.studio.dto.ConversationDTO;
import com.aigen.studio.dto.MessageDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
import com.aigen.studio.sdk.ICodingService;
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

    private static CountDownLatch uiGenerationStarted;
    private static CountDownLatch allowUiComplete;
    private static CountDownLatch codeGenerationStarted;
    private static CountDownLatch allowCodeComplete;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void resetLatches() {
        uiGenerationStarted = new CountDownLatch(1);
        allowUiComplete = new CountDownLatch(1);
        codeGenerationStarted = new CountDownLatch(1);
        allowCodeComplete = new CountDownLatch(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ICodingService codingService() {
            return new ICodingService() {
                @Override
                public void executeTask(String prompt, Path workDir, MessageHandler handler) {
                    uiGenerationStarted.countDown();
                    try {
                        allowUiComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    handler.onAssistantMessage("<html></html>");
                    handler.onComplete();
                }
            };
        }

        @Bean
        @Primary
        PromptTaskService promptTaskService() {
            return new PromptTaskService(null) {
                @Override
                public void generateCode(String irContent, Path outputPath, Consumer<String> logConsumer) {
                    codeGenerationStarted.countDown();
                    try {
                        allowCodeComplete.await(5, TimeUnit.SECONDS);
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
    void confirmUnderstandingReturnsBeforeUiGenerationCompletes() throws Exception {
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
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS));
        } finally {
            allowUiComplete.countDown();
            executor.shutdownNow();
        }

        Conversation updated = conversationRepository.findById(conversationId).orElseThrow();
        assertEquals(ConversationStage.UI_GENERATING, updated.getStage());

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        assertTrue(messages.stream().anyMatch(msg -> msg.getContent().contains("正在生成 UI 原型")));
    }

    @Test
    void confirmUiPrototypeEventuallyMarksReadyToStart() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_GENERATING);
        conversation.setUnderstandingConfirmed(false);
        conversation.setUiPrototypeContent("<html></html>");
        conversation.setUiConfirmed(false);
        conversation = conversationRepository.save(conversation);
        final Long conversationId = conversation.getId();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ConversationDTO> future = executor.submit(() ->
                conversationService.confirmUIPrototype(conversationId)
        );

        try {
            future.get(500, TimeUnit.MILLISECONDS);
            assertTrue(codeGenerationStarted.await(2, TimeUnit.SECONDS));
        } finally {
            allowCodeComplete.countDown();
            executor.shutdownNow();
        }

        Conversation updated = waitForStage(conversationId, ConversationStage.READY_TO_START, 5);
        assertTrue(updated.getGeneratedCodePath().startsWith(outputDir.toString()));
    }

    @Test
    void uiReadyStageSupportsRegenerateUiMessageWhenNoTaskRunning() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_READY);
        conversation.setUiPrototypeContent("<html></html>");
        conversation.setUiConfirmed(false);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("请重新生成 UI");

        try {
            MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
            assertNotNull(response);
            assertTrue(response.getContent().contains("重新生成 UI 原型"));
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS), "UI regeneration should be triggered");

            Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
            assertTrue(updated.getStage() == ConversationStage.UI_GENERATING
                    || updated.getStage() == ConversationStage.UI_READY);
        } finally {
            allowUiComplete.countDown();
        }
    }

    @Test
    void uiReadyStageRegenerateMessageReturnsBeforeUiGenerationCompletes() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_READY);
        conversation.setUiPrototypeContent("<html></html>");
        conversation.setUiConfirmed(false);
        conversation = conversationRepository.save(conversation);
        final Long conversationId = conversation.getId();

        MessageDTO message = new MessageDTO();
        message.setContent("重新生成UI");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MessageDTO> future = executor.submit(() ->
                conversationService.sendMessageToNewConversation(conversationId, message)
        );

        try {
            MessageDTO response = future.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(response);
            assertTrue(response.getContent().contains("重新生成 UI 原型"));
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS), "UI regeneration should be triggered");
        } finally {
            allowUiComplete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void uiGeneratingStageAllowsRegenerateUiWhenNoRealTaskRunning() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_GENERATING);
        conversation.setUiPrototypeContent(null);
        conversation.setUiConfirmed(false);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("重新生成UI");

        try {
            MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
            assertNotNull(response);
            assertTrue(response.getContent().contains("重新生成 UI 原型"));
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS), "UI regeneration should be triggered");
        } finally {
            allowUiComplete.countDown();
        }
    }

    @Test
    void readyToStartStageSupportsRequirementRefinementMessage() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Initial understanding");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.READY_TO_START);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("需求修改：增加一个报表分析页面");

        MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
        assertNotNull(response);
        assertTrue(response.getContent().contains("请问这个理解是否正确"));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.UNDERSTANDING_CONFIRMED, updated.getStage());
        assertTrue(updated.getUserRequirement().contains("报表分析页面"));
        assertEquals("understood", updated.getAiUnderstanding());
    }

    @Test
    void codeGeneratingStageAllowsRegenerateUiMessageWhenNoRealTaskRunning() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.CODE_GENERATING);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("重新生成 UI");

        try {
            MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
            assertNotNull(response);
            assertTrue(response.getContent().contains("重新生成 UI 原型"));
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS), "UI regeneration should be triggered");
        } finally {
            allowUiComplete.countDown();
        }
    }

    @Test
    void codeGeneratingStageRejectsRegenerateUiMessageWhenTaskIsActuallyRunning() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UI_GENERATING);
        conversation.setUiPrototypeContent("<html></html>");
        conversation.setUiConfirmed(false);
        conversation = conversationRepository.save(conversation);

        conversationService.confirmUIPrototype(conversation.getId());
        assertTrue(codeGenerationStarted.await(2, TimeUnit.SECONDS), "Code generation should be running");

        MessageDTO message = new MessageDTO();
        message.setContent("重新生成 UI");

        MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
        assertNotNull(response);
        assertTrue(response.getContent().contains("任务正在运行"));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.CODE_GENERATING, updated.getStage());

        allowCodeComplete.countDown();
    }

    @Test
    void codeGeneratingStageAllowsRegenerateUiMessageWhenConversationIsNotActive() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.FAILED);
        conversation.setStage(ConversationStage.CODE_GENERATING);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("重新生成 UI");

        try {
            MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
            assertNotNull(response);
            assertTrue(response.getContent().contains("重新生成 UI 原型"));
            assertTrue(uiGenerationStarted.await(2, TimeUnit.SECONDS), "UI regeneration should be triggered");

            Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
            assertTrue(updated.getStage() == ConversationStage.UI_GENERATING
                    || updated.getStage() == ConversationStage.UI_READY);
            assertEquals(Conversation.ConversationStatus.ACTIVE, updated.getStatus());
        } finally {
            allowUiComplete.countDown();
        }
    }

    @Test
    void previewingStageAcceptsIssueReportAsRequirementRefinement() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test");
        conversation.setUserRequirement("Generate a demo app");
        conversation.setAiUnderstanding("Initial understanding");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.PREVIEWING);
        conversation = conversationRepository.save(conversation);

        MessageDTO message = new MessageDTO();
        message.setContent("点击开始测试调出错，status 400，需要修改");

        MessageDTO response = conversationService.sendMessageToNewConversation(conversation.getId(), message);
        assertNotNull(response);
        assertTrue(response.getContent().contains("请问这个理解是否正确"));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.UNDERSTANDING_CONFIRMED, updated.getStage());
        assertTrue(updated.getUserRequirement().contains("status 400"));
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
