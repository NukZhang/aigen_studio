package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Value("${aigen.rag.embedding-batch-size:16}")
    private int batchSize = 16;

    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }

    public List<Embedding> embedAll(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        return embedAllSegments(segments);
    }

    public List<Embedding> embedAllSegments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        int effectiveBatchSize = batchSize <= 0 ? segments.size() : batchSize;
        if (segments.size() <= effectiveBatchSize) {
            return embeddingModel.embedAll(segments).content();
        }

        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (int start = 0; start < segments.size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, segments.size());
            List<TextSegment> batch = segments.subList(start, end);
            embeddings.addAll(embeddingModel.embedAll(batch).content());
        }
        return embeddings;
    }
}
