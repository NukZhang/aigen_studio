package com.aigen.studio.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationVectorService {

    private static final int CHUNK_MAX_CHARS = 1000;

    private final MessageRepository messageRepository;
    private final DocumentIngestionService documentIngestionService;
    private final RAGService ragService;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;
    private final Map<Long, CompletableFuture<VectorizationResult>> indexingTasks = new ConcurrentHashMap<>();

    public VectorizationResult indexConversation(Long conversationId) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages == null || messages.isEmpty()) {
            return new VectorizationResult(0, 0);
        }

        List<String> chunks = chunkMessages(messages, CHUNK_MAX_CHARS);
        int indexedSegments = 0;

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "conversation");
            metadata.put("conversationId", conversationId);
            metadata.put("chunkIndex", i + 1);
            metadata.put("totalChunks", chunks.size());

            DocumentIngestionService.IngestionResult result =
                    documentIngestionService.ingestText(chunks.get(i), metadata);
            indexedSegments += result.segmentCount();
        }

        log.info("Indexed conversation {}, chunks={}, segments={}", conversationId, chunks.size(), indexedSegments);
        return new VectorizationResult(chunks.size(), indexedSegments);
    }

    public List<EmbeddingMatch<TextSegment>> searchConversationHistory(Long conversationId, String query, int maxResults) {
        return ragService.search(query, maxResults).stream()
                .filter(match -> Objects.equals(String.valueOf(conversationId), match.embedded().metadata().get("conversationId")))
                .limit(maxResults)
                .toList();
    }

    public CompletableFuture<VectorizationResult> indexConversationAsync(Long conversationId) {
        if (conversationId == null) {
            return CompletableFuture.completedFuture(new VectorizationResult(0, 0));
        }

        CompletableFuture<VectorizationResult> current = indexingTasks.get(conversationId);
        if (current != null && !current.isDone()) {
            return current;
        }

        CompletableFuture<VectorizationResult> future = CompletableFuture.supplyAsync(
                () -> indexConversation(conversationId),
                taskExecutor
        );

        indexingTasks.put(conversationId, future);
        future.whenComplete((result, error) -> {
            indexingTasks.remove(conversationId);
            if (error != null) {
                log.warn("Async conversation vector indexing failed for {}", conversationId, error);
            } else {
                log.info("Async conversation vector indexing completed for {}, chunks={}, segments={}",
                        conversationId, result.chunkCount(), result.indexedSegments());
            }
        });

        return future;
    }

    public boolean isIndexing(Long conversationId) {
        CompletableFuture<VectorizationResult> future = indexingTasks.get(conversationId);
        return future != null && !future.isDone();
    }

    private List<String> chunkMessages(List<Message> messages, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Message message : messages) {
            String line = "[" + message.getRole() + "] " + safeText(message.getContent());
            if (line.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + line.length() + 1 > maxChars) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record VectorizationResult(int chunkCount, int indexedSegments) {
    }
}
