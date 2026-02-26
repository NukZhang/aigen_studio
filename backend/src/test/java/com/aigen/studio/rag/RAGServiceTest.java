package com.aigen.studio.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGServiceTest {

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @InjectMocks
    private RAGService ragService;

    @Test
    void searchDelegatesToVectorStoreService() {
        List<EmbeddingMatch<TextSegment>> expected = List.of(match("需求片段", 0.91, 1L));
        when(vectorStoreService.search("订单系统", 3)).thenReturn(expected);

        List<EmbeddingMatch<TextSegment>> actual = ragService.search("订单系统", 3);

        assertEquals(1, actual.size());
        assertEquals("需求片段", actual.get(0).embedded().text());
    }

    @Test
    void generateWithRagIncludesRetrievedContext() {
        List<EmbeddingMatch<TextSegment>> matches = List.of(
                match("订单管理功能", 0.95, 9L),
                match("用户资料维护", 0.73, 9L)
        );
        when(vectorStoreService.search("订单查询", 5)).thenReturn(matches);
        when(chatLanguageModel.generate(anyString())).thenReturn("RAG answer");

        String response = ragService.generateWithRAG("订单查询", 9L);

        assertEquals("RAG answer", response);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatLanguageModel).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("订单管理功能"));
        assertTrue(prompt.contains("用户资料维护"));
        assertTrue(prompt.contains("订单查询"));
    }

    @Test
    void generateWithRagFiltersByConversationId() {
        List<EmbeddingMatch<TextSegment>> matches = List.of(
                match("conversation-1 片段", 0.9, 1L),
                match("conversation-2 片段", 0.9, 2L)
        );
        when(vectorStoreService.search("问题", 5)).thenReturn(matches);
        when(chatLanguageModel.generate(anyString())).thenReturn("ok");

        ragService.generateWithRAG("问题", 1L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatLanguageModel).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("conversation-1 片段"));
        assertTrue(!prompt.contains("conversation-2 片段"));
    }

    private EmbeddingMatch<TextSegment> match(String text, double score, Long conversationId) {
        Metadata metadata = new Metadata();
        metadata.put("conversationId", String.valueOf(conversationId));
        TextSegment segment = TextSegment.from(text, metadata);
        return new EmbeddingMatch<>(score, "id-" + text.hashCode(), Embedding.from(new float[]{1.0f, 0.0f}), segment);
    }
}
