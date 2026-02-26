package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void embedReturnsModelEmbedding() {
        Embedding embedding = Embedding.from(new float[]{1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed("phase1 test")).thenReturn(Response.from(embedding));

        Embedding result = embeddingService.embed("phase1 test");

        assertNotNull(result);
        assertEquals(3, result.dimension());
        verify(embeddingModel).embed("phase1 test");
    }

    @Test
    void embedAllConvertsInputTextsToSegments() {
        Embedding first = Embedding.from(new float[]{1.0f, 0.0f});
        Embedding second = Embedding.from(new float[]{0.0f, 1.0f});
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(first, second)));

        List<Embedding> embeddings = embeddingService.embedAll(List.of("alpha", "beta"));

        assertEquals(2, embeddings.size());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embedAll(captor.capture());
        List captured = captor.getValue();
        assertEquals(2, captured.size());
    }
}
