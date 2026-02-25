package com.aigen.studio.service;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.repository.MessageRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final AtomicReference<String> receivedPrompt = new AtomicReference<>();
    private static final List<String> receivedPrompts = new CopyOnWriteArrayList<>();
    private static final AtomicReference<RuntimeException> executeException = new AtomicReference<>();
    private static CountDownLatch taskInvoked;
    private static final AtomicReference<Consumer<ICodingService.MessageHandler>> handlerHook = new AtomicReference<>();

    @Autowired
    private UIPrototypeService uiPrototypeService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void resetTracking() {
        workDirExistsAtCall.set(false);
        receivedWorkDir.set(null);
        receivedPrompt.set(null);
        receivedPrompts.clear();
        executeException.set(null);
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
                    receivedPrompt.set(prompt);
                    receivedPrompts.add(prompt);
                    receivedWorkDir.set(workDir);
                    workDirExistsAtCall.set(Files.isDirectory(workDir));
                    taskInvoked.countDown();
                    RuntimeException toThrow = executeException.get();
                    if (toThrow != null) {
                        throw toThrow;
                    }
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

        Conversation updated = waitForStage(conversation.getId(), ConversationStage.UI_READY, 2);
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

        Conversation updated = waitForStage(conversationId, ConversationStage.UI_READY, 2);
        assertEquals("UI_READY", updated.getStage().name());
        assertNotNull(updated.getUiPrototypeContent());
        assertTrue(updated.getUiPrototypeContent().contains(html));
        assertTrue(updated.getUiPrototypeContent().contains("pencil-ui-design"));
    }

    @Test
    void generateUIPrototypeLoadsHtmlFromNonIndexFileWhenAssistantEmpty() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        String html = "<html><body>Non Index UI</body></html>";
        CountDownLatch completed = new CountDownLatch(1);
        Long conversationId = conversation.getId();
        handlerHook.set(handler -> {
            try {
                Path htmlFile = outputDir
                        .resolve("conversation-" + conversationId)
                        .resolve("ui-prototype")
                        .resolve("new-year-ranking.html");
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

        Conversation updated = waitForStage(conversationId, ConversationStage.UI_READY, 2);
        assertEquals("UI_READY", updated.getStage().name());
        assertNotNull(updated.getUiPrototypeContent());
        assertTrue(updated.getUiPrototypeContent().contains(html));
        assertTrue(updated.getUiPrototypeContent().contains("pencil-ui-design"));
    }

    @Test
    void generateUIPrototypeSendsHeartbeatWhileWaitingForModelResponse() {
        ReflectionTestUtils.setField(uiPrototypeService, "uiHeartbeatIntervalMillis", 50L);
        ReflectionTestUtils.setField(uiPrototypeService, "uiHeartbeatInitialDelayMillis", 10L);

        Conversation conversation = new Conversation();
        conversation.setProjectName("Heartbeat Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        CountDownLatch completed = new CountDownLatch(1);
        handlerHook.set(handler -> {
            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handler.onAssistantMessage("<html><body>Heartbeat OK</body></html>");
            handler.onComplete();
            completed.countDown();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitLatch(completed));
        Conversation updated = waitForStage(conversation.getId(), ConversationStage.UI_READY, 2);
        assertEquals(ConversationStage.UI_READY, updated.getStage());

        List<Message> systemMessages = messageRepository.findByConversationIdAndRoleOrderByCreatedAtAsc(
                conversation.getId(),
                Message.MessageRole.SYSTEM
        );
        assertTrue(
                systemMessages.stream().anyMatch(msg -> msg.getContent() != null && msg.getContent().contains("UI 原型生成中")),
                "Should emit heartbeat system message while waiting for model response"
        );
    }

    @Test
    void generateUIPrototypeRewritesEmptyExistingHtmlFileOnSuccess() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        Path htmlFile = outputDir
                .resolve("conversation-" + conversation.getId())
                .resolve("ui-prototype")
                .resolve("index.html");
        try {
            Files.createDirectories(htmlFile.getParent());
            Files.writeString(htmlFile, "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String html = "<html><body>From Assistant</body></html>";
        CountDownLatch completed = new CountDownLatch(1);
        handlerHook.set(handler -> {
            handler.onAssistantMessage(html);
            handler.onComplete();
            completed.countDown();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitLatch(completed));
        Conversation updated = waitForStage(conversation.getId(), ConversationStage.UI_READY, 2);
        assertEquals(ConversationStage.UI_READY, updated.getStage());
        try {
            String savedFile = Files.readString(htmlFile);
            assertTrue(savedFile.contains("From Assistant"));
            assertTrue(savedFile.contains("<html>"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateUIPrototypePromptRendersBackendTemplateVariables() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Template Driven Project");
        conversation.setUserRequirement("Template requirement text");
        conversation.setAiUnderstanding("Template understanding text");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitTaskInvocation());
        String prompt = receivedPrompt.get();
        assertNotNull(prompt);
        assertTrue(prompt.contains("模板模式：PRIMARY"),
                "Primary prompt should be rendered from backend prompt template");
        assertTrue(prompt.contains("Template Driven Project"));
        assertTrue(prompt.contains("Template requirement text"));
        assertTrue(prompt.contains("Template understanding text"));
        assertTrue(prompt.contains("images.unsplash.com"),
                "Rendered template prompt should keep Unsplash requirements");
        assertTrue(!prompt.contains("{{PROJECT_NAME}}"),
                "Template variable placeholders should be rendered");
        assertTrue(!prompt.contains("{{USER_REQUIREMENT}}"),
                "Template variable placeholders should be rendered");
        assertTrue(!prompt.contains("{{AI_UNDERSTANDING}}"),
                "Template variable placeholders should be rendered");
    }

    @Test
    void buildPromptDiagnosticsMarksPencilAndVisualRules() {
        String prompt = """
                pencil-ui-design
                图片
                背景
                图表
                头像
                """;

        String diagnostics = uiPrototypeService.buildPromptDiagnostics(prompt);

        assertTrue(diagnostics.contains("pencil=true"));
        assertTrue(diagnostics.contains("image=true"));
        assertTrue(diagnostics.contains("background=true"));
        assertTrue(diagnostics.contains("chart=true"));
        assertTrue(diagnostics.contains("avatar=true"));
    }

    @Test
    void generateUIPrototypeMarksFailedWhenExecuteTaskThrows() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        executeException.set(new RuntimeException("生成数据错误"));

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitTaskInvocation());
        Conversation updated = waitForStage(conversation.getId(), ConversationStage.FAILED, 2);
        assertEquals(ConversationStage.FAILED, updated.getStage());
        assertTrue(updated.getErrorMessage() != null && updated.getErrorMessage().contains("生成数据错误"));
    }

    @Test
    void generateUIPrototypeMarksFailedWhenNoValidHtmlProduced() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        CountDownLatch completed = new CountDownLatch(1);
        handlerHook.set(handler -> {
            handler.onComplete();
            completed.countDown();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        assertTrue(awaitLatch(completed));
        Conversation updated = waitForStage(conversation.getId(), ConversationStage.FAILED, 2);
        assertEquals(ConversationStage.FAILED, updated.getStage());
        assertTrue(updated.getErrorMessage() != null && updated.getErrorMessage().contains("有效 HTML"));
    }

    @Test
    void generateUIPrototypeDoesNotRetryAfterFirstResponseTimeout() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        handlerHook.set(handler -> {
            attemptCounter.incrementAndGet();
            handler.onError(new java.util.concurrent.TimeoutException(
                    "Did not observe any item or terminal signal within first signal from a Publisher in 'peek'"));
            handler.onComplete();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        Conversation updated = waitForStage(conversation.getId(), ConversationStage.FAILED, 3);
        assertEquals(ConversationStage.FAILED, updated.getStage());
        assertTrue(updated.getErrorMessage() != null && updated.getErrorMessage().contains("Did not observe any item"));
        assertEquals(1, attemptCounter.get(), "Should not retry after first-response timeout");
        assertEquals(1, receivedPrompts.size(), "Should invoke coding service once");
        assertTrue(receivedPrompts.get(0).contains("模板模式：PRIMARY"),
                "Attempt should use primary prompt section");
        assertTrue(receivedPrompts.get(0).contains("images.unsplash.com"),
                "Primary prompt should include Unsplash placeholder rule");
    }

    @Test
    void generateUIPrototypeInjectsPencilAttributionWhenMissingInModelOutput() {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Test Project");
        conversation.setUserRequirement("Generate a UI prototype");
        conversation.setAiUnderstanding("Understood requirements");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.UNDERSTANDING_CONFIRMED);
        conversation = conversationRepository.save(conversation);

        handlerHook.set(handler -> {
            handler.onAssistantMessage("<html><body>No attribution</body></html>");
            handler.onComplete();
        });

        uiPrototypeService.generateUIPrototype(conversation.getId());

        Conversation updated = waitForStage(conversation.getId(), ConversationStage.UI_READY, 2);
        assertEquals(ConversationStage.UI_READY, updated.getStage());
        assertNotNull(updated.getUiPrototypeContent());
        assertTrue(updated.getUiPrototypeContent().contains("pencil-ui-design"),
                "Saved HTML should include pencil-ui-design attribution marker");
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

    private Conversation waitForStage(Long conversationId, ConversationStage stage, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Conversation conversation = null;
        do {
            conversation = conversationRepository.findById(conversationId).orElseThrow();
            if (conversation.getStage() == stage) {
                return conversation;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return conversation;
            }
        } while (System.currentTimeMillis() < deadline);
        return conversation;
    }
}
