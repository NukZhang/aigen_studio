package com.aigen.studio.sdk.iflow;

import cn.iflow.sdk.core.IFlowClient;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

}
