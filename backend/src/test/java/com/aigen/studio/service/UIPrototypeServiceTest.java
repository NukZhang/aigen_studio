package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.sdk.ICodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ui-prototype-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UIPrototypeServiceTest {

    @TempDir
    static Path outputDir;

    private static final AtomicBoolean workDirExistsAtCall = new AtomicBoolean(false);
    private static final AtomicReference<Path> receivedWorkDir = new AtomicReference<>();
    private static CountDownLatch taskInvoked;
    private static final AtomicReference<Consumer<ICodingService.MessageHandler>> handlerHook = new AtomicReference<>();

    @Autowired
    private UIPrototypeService uiPrototypeService;

    @Autowired
    private ConversationRepository conversationRepository;

    @BeforeEach
    void resetTracking() {
        workDirExistsAtCall.set(false);
        receivedWorkDir.set(null);
        taskInvoked = new CountDownLatch(1);
        handlerHook.set(null);
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ICodingService codingService() {
            return new ICodingService() {
                @Override
                public void executeTask(String prompt, Path workDir, MessageHandler handler) {
                    receivedWorkDir.set(workDir);
                    workDirExistsAtCall.set(Files.isDirectory(workDir));
                    taskInvoked.countDown();
                    Consumer<ICodingService.MessageHandler> hook = handlerHook.get();
                    if (hook != null) {
                        hook.accept(handler);
                    }
                }
            };
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("iflow.sdk.output-dir", () -> outputDir.toString());
    }

    @Test
    void generateUIPrototypeCreatesWorkDirBeforeExecuteTask() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        uiPrototypeService.generateUIPrototype(conversation.getId());

        Path expectedDir = outputDir
                .resolve("conversation-" + conversation.getId())
                .resolve("ui-prototype");

        assertTrue(awaitTaskInvocation());
        assertNotNull(receivedWorkDir.get());
        assertTrue(workDirExistsAtCall.get(), "Work dir should exist before executeTask is called");
        assertTrue(Files.isDirectory(expectedDir));
    }

    @Test
    void generateUIPrototypeUpdatesStageAndPathOnComplete() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        String html = "<html><body>UI Ready</body></html>";
        CountDownLatch completed = new CountDownLatch(1);
        handlerHook.set(handler -> {
            handler.onAssistantMessage(html);
            handler.onTaskFinish("END_TURN");
            handler.onComplete();
            completed.countDown();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitLatch(completed));

        Conversation updated = conversationRepository.findById(conversation.getId())
                .orElseThrow();
        assertEquals("UI_READY", updated.getStage().name());
        assertNotNull(updated.getUiPrototypePath());
        Path expectedPath = outputDir
                .resolve("conversation-" + conversation.getId())
                .resolve("ui-prototype")
                .resolve("index.html");
        assertEquals(expectedPath.toString(), updated.getUiPrototypePath());
        assertNotNull(updated.getUiPrototypeContent());
    }

    @Test
    void generateUIPrototypeLoadsHtmlFromFileWhenAssistantEmpty() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        String html = "<html><body>File UI</body></html>";
        CountDownLatch completed = new CountDownLatch(1);
        Long conversationId = conversation.getId();
        handlerHook.set(handler -> {
            try {
                Path htmlFile = outputDir
                        .resolve("conversation-" + conversationId)
                        .resolve("ui-prototype")
                        .resolve("index.html");
                Files.createDirectories(htmlFile.getParent());
                Files.writeString(htmlFile, html);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            handler.onComplete();
            completed.countDown();
        });

        uiPrototypeService.generateUIPrototype(conversationId);

        assertTrue(awaitLatch(completed));

        Conversation updated = conversationRepository.findById(conversationId)
                .orElseThrow();
        assertEquals("UI_READY", updated.getStage().name());
        assertEquals(html, updated.getUiPrototypeContent());
    }

    private boolean awaitTaskInvocation() {
        try {
            return taskInvoked.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
