package com.aigen.studio.sdk.iflow;

import cn.iflow.sdk.core.IFlowClient;
import cn.iflow.sdk.types.enums.MessageType;
import cn.iflow.sdk.types.enums.StopReason;
import cn.iflow.sdk.types.enums.ToolCallStatus;
import cn.iflow.sdk.types.messages.Message;
import cn.iflow.sdk.types.messages.TaskFinishMessage;
import cn.iflow.sdk.types.messages.ToolResultMessage;
import com.aigen.studio.sdk.ICodingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IFlowClientHelperTest {

    @TempDir
    Path workDir;

    @Test
    void formatToolResultContentHandlesNullContent() throws Exception {
        IFlowClientHelper helper = new IFlowClientHelper();
        ToolResultMessage message = new ToolResultMessage(
                "id-1",
                ToolCallStatus.COMPLETED,
                "tool",
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method method = IFlowClientHelper.class.getDeclaredMethod(
                "formatToolResultContent",
                ToolResultMessage.class
        );
        method.setAccessible(true);

        String result = (String) method.invoke(helper, message);
        assertEquals("", result);
    }

    @Test
    void executeTaskStopsOnTaskFinishWithoutTimeout() {
        Flux<Message> messages = Flux.concat(
                Mono.just(new TaskFinishMessage(StopReason.END_TURN)),
                Flux.never()
        );
        StubIFlowClient client = new StubIFlowClient(messages);

        IFlowClientHelper helper = new IFlowClientHelper() {
            @Override
            public IFlowClient createClient(Path ignored) {
                return client;
            }
        };
        ReflectionTestUtils.setField(helper, "timeoutMillis", 200L);

        AtomicBoolean completeCalled = new AtomicBoolean(false);
        AtomicBoolean taskFinishCalled = new AtomicBoolean(false);

        Thread runner = new Thread(() -> helper.executeTask("prompt", workDir, new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                // no-op
            }

            @Override
            public void onToolCall(String toolName, String status) {
                // no-op
            }

            @Override
            public void onToolResult(String content) {
                // no-op
            }

            @Override
            public void onTaskFinish(String stopReason) {
                taskFinishCalled.set(true);
            }

            @Override
            public void onError(Throwable error) {
                // no-op
            }

            @Override
            public void onComplete() {
                completeCalled.set(true);
            }
        }));

        runner.start();
        try {
            runner.join(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(taskFinishCalled.get(), "Task finish should be observed");
        assertTrue(completeCalled.get(), "Completion should be observed quickly after task finish");
        assertFalse(runner.isAlive(), "Execution should stop after task finish without waiting for timeout");

        if (runner.isAlive()) {
            try {
                runner.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void executeTaskAllowsLongerFirstResponseWindow() {
        Flux<Message> messages = Flux.concat(
                Mono.delay(Duration.ofMillis(120))
                        .map(ignored -> (Message) new TaskFinishMessage(StopReason.END_TURN)),
                Flux.never()
        );
        StubIFlowClient client = new StubIFlowClient(messages);

        IFlowClientHelper helper = new IFlowClientHelper() {
            @Override
            public IFlowClient createClient(Path ignored) {
                return client;
            }
        };
        ReflectionTestUtils.setField(helper, "timeoutMillis", 80L);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicBoolean taskFinishCalled = new AtomicBoolean(false);

        helper.executeTask("prompt", workDir, new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                // no-op
            }

            @Override
            public void onToolCall(String toolName, String status) {
                // no-op
            }

            @Override
            public void onToolResult(String content) {
                // no-op
            }

            @Override
            public void onTaskFinish(String stopReason) {
                taskFinishCalled.set(true);
            }

            @Override
            public void onError(Throwable error) {
                errorCalled.set(true);
            }

            @Override
            public void onComplete() {
                // no-op
            }
        });

        assertTrue(taskFinishCalled.get(), "Task finish should be observed");
        assertFalse(errorCalled.get(), "Should not timeout before first response");
    }

    @Test
    void executeTaskStillTimesOutOnInactivityAfterFirstMessage() {
        Flux<Message> messages = Flux.concat(
                Mono.delay(Duration.ofMillis(20)).map(ignored -> (Message) new ToolResultMessage(
                        "id-2",
                        ToolCallStatus.COMPLETED,
                        "tool",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                Flux.never()
        );
        StubIFlowClient client = new StubIFlowClient(messages);

        IFlowClientHelper helper = new IFlowClientHelper() {
            @Override
            public IFlowClient createClient(Path ignored) {
                return client;
            }
        };
        ReflectionTestUtils.setField(helper, "timeoutMillis", 80L);
        ReflectionTestUtils.setField(helper, "firstResponseTimeoutBufferMillis", 0L);
        ReflectionTestUtils.setField(helper, "toolInactivityTimeoutMillis", 80L);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        helper.executeTask("prompt", workDir, new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                // no-op
            }

            @Override
            public void onToolCall(String toolName, String status) {
                // no-op
            }

            @Override
            public void onToolResult(String content) {
                // no-op
            }

            @Override
            public void onTaskFinish(String stopReason) {
                // no-op
            }

            @Override
            public void onError(Throwable error) {
                errorCalled.set(true);
                errorMessage.set(error.getMessage());
            }

            @Override
            public void onComplete() {
                // no-op
            }
        });

        assertTrue(errorCalled.get(), "Should timeout when no follow-up message arrives in inactivity window");
        assertTrue(errorMessage.get().contains("phase=inactivity_timeout_after_messages"));
    }

    @Test
    void executeTaskIgnoresNonTaskMessagesForTimeoutProgress() {
        Flux<Message> messages = Flux.interval(Duration.ofMillis(15))
                .map(ignored -> (Message) new NonTaskMessage())
                .onBackpressureDrop();
        StubIFlowClient client = new StubIFlowClient(messages);

        IFlowClientHelper helper = new IFlowClientHelper() {
            @Override
            public IFlowClient createClient(Path ignored) {
                return client;
            }
        };
        ReflectionTestUtils.setField(helper, "timeoutMillis", 80L);
        ReflectionTestUtils.setField(helper, "firstResponseTimeoutBufferMillis", 0L);

        AtomicBoolean errorCalled = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        helper.executeTask("prompt", workDir, new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                // no-op
            }

            @Override
            public void onToolCall(String toolName, String status) {
                // no-op
            }

            @Override
            public void onToolResult(String content) {
                // no-op
            }

            @Override
            public void onTaskFinish(String stopReason) {
                // no-op
            }

            @Override
            public void onError(Throwable error) {
                errorCalled.set(true);
                errorMessage.set(error.getMessage());
            }

            @Override
            public void onComplete() {
                // no-op
            }
        });

        assertTrue(errorCalled.get(), "Should timeout waiting for first task message");
        assertTrue(errorMessage.get().contains("phase=first_response_timeout"));
    }

    @Test
    void describeTimeoutPhaseReturnsFirstResponseTimeoutWhenNoMessagesArrive() {
        IFlowClientHelper helper = new IFlowClientHelper();
        String phase = helper.describeTimeoutPhase(0, -1L, -1L);
        assertEquals("first_response_timeout", phase);
    }

    @Test
    void describeTimeoutPhaseReturnsInactivityTimeoutWhenMessagesAlreadyArrived() {
        IFlowClientHelper helper = new IFlowClientHelper();
        String phase = helper.describeTimeoutPhase(3, 120L, 320L);
        assertEquals("inactivity_timeout_after_messages", phase);
    }

    @Test
    void resolveInactivityTimeoutKeepsDefaultWhenSkillNotDetected() throws Exception {
        IFlowClientHelper helper = new IFlowClientHelper();
        ReflectionTestUtils.setField(helper, "toolInactivityTimeoutMillis", 200L);
        ReflectionTestUtils.setField(helper, "skillInactivityTimeoutMillis", 200L);

        Method method = IFlowClientHelper.class.getDeclaredMethod(
                "resolveInactivityTimeout",
                long.class,
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);

        long result = (long) method.invoke(helper, 80L, false, false);
        assertEquals(80L, result);
    }

    @Test
    void resolveInactivityTimeoutExtendsWhenSkillDetected() throws Exception {
        IFlowClientHelper helper = new IFlowClientHelper();
        ReflectionTestUtils.setField(helper, "toolInactivityTimeoutMillis", 150L);
        ReflectionTestUtils.setField(helper, "skillInactivityTimeoutMillis", 200L);

        Method method = IFlowClientHelper.class.getDeclaredMethod(
                "resolveInactivityTimeout",
                long.class,
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);

        long result = (long) method.invoke(helper, 80L, true, true);
        assertEquals(200L, result);
    }

    @Test
    void resolveInactivityTimeoutExtendsWhenToolActivityDetected() throws Exception {
        IFlowClientHelper helper = new IFlowClientHelper();
        ReflectionTestUtils.setField(helper, "toolInactivityTimeoutMillis", 150L);
        ReflectionTestUtils.setField(helper, "skillInactivityTimeoutMillis", 200L);

        Method method = IFlowClientHelper.class.getDeclaredMethod(
                "resolveInactivityTimeout",
                long.class,
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);

        long result = (long) method.invoke(helper, 80L, true, false);
        assertEquals(150L, result);
    }

    private static final class StubIFlowClient implements IFlowClient {
        private final Flux<Message> messages;

        private StubIFlowClient(Flux<Message> messages) {
            this.messages = messages;
        }

        @Override
        public Mono<Void> connect() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> loadSession(String sessionId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> disconnect() {
            return Mono.empty();
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public Mono<Void> sendMessage(String text) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(String text, List<Path> files) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> interrupt() {
            return Mono.empty();
        }

        @Override
        public Flux<Message> receiveMessages() {
            return messages;
        }

        @Override
        public cn.iflow.sdk.types.config.IFlowOptions getOptions() {
            return null;
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private static final class NonTaskMessage implements Message {
        @Override
        public MessageType getType() {
            return MessageType.PLAN;
        }

        @Override
        public long getTimestamp() {
            return System.currentTimeMillis();
        }

        @Override
        public java.util.Optional<String> getAgentId() {
            return java.util.Optional.empty();
        }
    }

}
