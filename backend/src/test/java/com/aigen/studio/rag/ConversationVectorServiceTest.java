package com.aigen.studio.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import com.aigen.studio.entity.Message;
import com.aigen.studio.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationVectorServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private DocumentIngestionService documentIngestionService;

    @Mock
    private RAGService ragService;

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private ConversationVectorService conversationVectorService;

    @Test
    void indexConversationChunksMessagesAndIngestsWithMetadata() {
        Message user = new Message();
        user.setRole(Message.MessageRole.USER);
        user.setContent("请帮我做一个订单管理系统");
        Message assistant = new Message();
        assistant.setRole(Message.MessageRole.ASSISTANT);
        assistant.setContent("好的，我先分析模块边界");

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(101L))
                .thenReturn(List.of(user, assistant));
        when(documentIngestionService.ingestText(anyString(), anyMap()))
                .thenReturn(new DocumentIngestionService.IngestionResult(1, List.of("seg-1")));

        ConversationVectorService.VectorizationResult result = conversationVectorService.indexConversation(101L);

        assertEquals(1, result.chunkCount());

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(documentIngestionService, atLeastOnce()).ingestText(anyString(), metadataCaptor.capture());
        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals("conversation", metadata.get("source"));
        assertEquals("101", String.valueOf(metadata.get("conversationId")));
    }

    @Test
    void searchConversationHistoryReturnsOnlyCurrentConversation() {
        when(ragService.search("订单", 5)).thenReturn(List.of(
                match("conv-1 result", 1L),
                match("conv-2 result", 2L)
        ));

        List<EmbeddingMatch<TextSegment>> matches = conversationVectorService.searchConversationHistory(1L, "订单", 5);

        assertEquals(1, matches.size());
        assertEquals("conv-1 result", matches.get(0).embedded().text());
        assertTrue(matches.stream().noneMatch(m -> m.embedded().text().contains("conv-2")));
    }

    @Test
    void indexConversationAsyncUsesExecutorAndReturnsResult() {
        Message user = new Message();
        user.setRole(Message.MessageRole.USER);
        user.setContent("我要做一个任务看板");

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(202L))
                .thenReturn(List.of(user));
        when(documentIngestionService.ingestText(anyString(), anyMap()))
                .thenReturn(new DocumentIngestionService.IngestionResult(1, List.of("seg-1")));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        ConversationVectorService.VectorizationResult result =
                conversationVectorService.indexConversationAsync(202L).join();

        assertEquals(1, result.chunkCount());
        assertEquals(1, result.indexedSegments());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    private EmbeddingMatch<TextSegment> match(String text, Long conversationId) {
        Metadata metadata = new Metadata();
        metadata.put("conversationId", String.valueOf(conversationId));
        return new EmbeddingMatch<>(
                0.9,
                "id-" + conversationId,
                Embedding.from(new float[]{1.0f, 0.0f}),
                TextSegment.from(text, metadata)
        );
    }
}
