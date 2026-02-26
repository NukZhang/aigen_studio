package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        vectorStoreService = new VectorStoreService(new InMemoryEmbeddingStore<>(), embeddingService);
    }

    @Test
    void addTextStoresEmbeddingAndReturnsId() {
        when(embeddingService.embed("dashboard ui requirement"))
                .thenReturn(Embedding.from(new float[]{1.0f, 0.0f, 0.0f}));

        String id = vectorStoreService.addText("dashboard ui requirement");

        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void searchReturnsMostRelevantSegments() {
        when(embeddingService.embed("order management module"))
                .thenReturn(Embedding.from(new float[]{1.0f, 0.0f, 0.0f}));
        when(embeddingService.embed("user profile center"))
                .thenReturn(Embedding.from(new float[]{0.0f, 1.0f, 0.0f}));
        when(embeddingService.embed("order query"))
                .thenReturn(Embedding.from(new float[]{1.0f, 0.0f, 0.0f}));

        vectorStoreService.addText("order management module");
        vectorStoreService.addText("user profile center");

        List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search("order query", 1);

        assertEquals(1, matches.size());
        assertEquals("order management module", matches.get(0).embedded().text());
    }
}
