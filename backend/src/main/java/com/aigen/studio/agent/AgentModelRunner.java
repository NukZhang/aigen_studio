package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Slf4j
final class AgentModelRunner {

    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private AgentModelRunner() {
    }

    static ModelExecutionResult generateWithHeartbeat(
            String agentName,
            ChatLanguageModel chatLanguageModel,
            String prompt,
            Supplier<String> fallbackSupplier
    ) {
        CompletableFuture<String> generationFuture = CompletableFuture.supplyAsync(() -> chatLanguageModel.generate(prompt));
        long startAt = System.currentTimeMillis();

        while (true) {
            try {
                String content = generationFuture.get(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                return ModelExecutionResult.success(normalize(content));
            } catch (TimeoutException e) {
                long elapsed = System.currentTimeMillis() - startAt;
                log.info("{} heartbeat: model call still running, elapsedMs={}", agentName, elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fallback(agentName, "interrupted", false, e, fallbackSupplier);
            } catch (ExecutionException e) {
                Throwable rootCause = rootCause(e);
                boolean timeout = isTimeoutException(rootCause);
                String reason = timeout ? "timeout" : "model_error";
                return fallback(agentName, reason, timeout, rootCause, fallbackSupplier);
            }
        }
    }

    private static ModelExecutionResult fallback(
            String agentName,
            String reason,
            boolean timeout,
            Throwable throwable,
            Supplier<String> fallbackSupplier
    ) {
        log.warn("{} model call failed, fallback activated, reason={}, cause={}",
                agentName, reason, throwable.toString());
        return ModelExecutionResult.fallback(normalize(fallbackSupplier.get()), timeout, reason);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    record ModelExecutionResult(String content, boolean fallbackUsed, boolean timeout, String reason) {

        static ModelExecutionResult success(String content) {
            return new ModelExecutionResult(content, false, false, "ok");
        }

        static ModelExecutionResult fallback(String content, boolean timeout, String reason) {
            return new ModelExecutionResult(content, true, timeout, reason);
        }
    }
}
