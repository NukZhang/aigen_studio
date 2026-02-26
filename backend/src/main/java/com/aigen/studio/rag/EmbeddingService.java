package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

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
        return embeddingModel.embedAll(segments).content();
    }
}
