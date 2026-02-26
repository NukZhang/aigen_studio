package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingService embeddingService;

    public String addText(String text) {
        List<String> ids = addSegments(List.of(TextSegment.from(text)));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public List<String> addSegments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<Embedding> embeddings = embeddingService.embedAllSegments(segments);
        return embeddingStore.addAll(embeddings, segments);
    }

    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingService.embed(query);
        return embeddingStore.findRelevant(queryEmbedding, maxResults);
    }
}
