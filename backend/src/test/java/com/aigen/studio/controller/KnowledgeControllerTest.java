package com.aigen.studio.controller;

import com.aigen.studio.rag.ConversationVectorService;
import com.aigen.studio.rag.DocumentIngestionService;
import com.aigen.studio.rag.RAGService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestionService documentIngestionService;

    @MockBean
    private RAGService ragService;

    @MockBean
    private ConversationVectorService conversationVectorService;

    @Test
    void ingestTextEndpointAcceptsContentAndReturnsSegmentInfo() throws Exception {
        when(documentIngestionService.ingestMarkdown(anyString(), anyMap()))
                .thenReturn(new DocumentIngestionService.IngestionResult(2, List.of("s1", "s2")));

        mockMvc.perform(post("/knowledge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "# 需求文档\\n\\n订单模块",
                                  "metadata": {
                                    "source": "spec"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.segmentIds[0]").value("s1"));
    }

    @Test
    void uploadEndpointIngestsMultipartFile() throws Exception {
        when(documentIngestionService.ingestMultipartFile(any(), anyMap()))
                .thenReturn(new DocumentIngestionService.IngestionResult(1, List.of("f1")));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "knowledge.md",
                "text/markdown",
                "# 标题\n正文".getBytes()
        );

        mockMvc.perform(multipart("/knowledge/upload")
                        .file(file)
                        .param("source", "manual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentCount").value(1))
                .andExpect(jsonPath("$.segmentIds[0]").value("f1"));

        verify(documentIngestionService).ingestMultipartFile(any(), anyMap());
    }

    @Test
    void searchEndpointReturnsGlobalKnowledgeMatches() throws Exception {
        when(ragService.search("订单", 5)).thenReturn(List.of(match("订单核心流程", 0.88, 1L)));

        mockMvc.perform(get("/knowledge/search")
                        .param("q", "订单")
                        .param("maxResults", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("订单核心流程"))
                .andExpect(jsonPath("$[0].score").value(0.88));
    }

    @Test
    void searchEndpointUsesConversationHistoryWhenConversationIdProvided() throws Exception {
        when(conversationVectorService.searchConversationHistory(9L, "登录", 3))
                .thenReturn(List.of(match("会话内历史片段", 0.77, 9L)));

        mockMvc.perform(get("/knowledge/search")
                        .param("q", "登录")
                        .param("maxResults", "3")
                        .param("conversationId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("会话内历史片段"));
    }

    @Test
    void cacheStatsEndpointReturnsRagCacheMetrics() throws Exception {
        when(ragService.getCacheStats()).thenReturn(new RAGService.CacheStats(12, 5, 1, 3));

        mockMvc.perform(get("/knowledge/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").value(12))
                .andExpect(jsonPath("$.misses").value(5))
                .andExpect(jsonPath("$.evictions").value(1))
                .andExpect(jsonPath("$.size").value(3));
    }

    @Test
    void cacheAlertEndpointReturnsAlertStatus() throws Exception {
        when(ragService.getCacheAlertStatus()).thenReturn(
                new RAGService.CacheAlertStatus(true, true, 30, 0.8, 6, 24, 2, 10,
                        20, 0.6, "cache miss rate exceeded threshold")
        );

        mockMvc.perform(get("/knowledge/cache/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.triggered").value(true))
                .andExpect(jsonPath("$.totalRequests").value(30))
                .andExpect(jsonPath("$.missRate").value(0.8))
                .andExpect(jsonPath("$.message").value("cache miss rate exceeded threshold"));
    }

    @Test
    void performanceStatsEndpointReturnsLatencyMetrics() throws Exception {
        when(ragService.getPerformanceStats()).thenReturn(
                new RAGService.PerformanceStats(18, 12.5, 20.2, 41.7, 100.0, 10, 18, false)
        );

        mockMvc.perform(get("/knowledge/perf/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSearches").value(18))
                .andExpect(jsonPath("$.avgLatencyMs").value(12.5))
                .andExpect(jsonPath("$.p95LatencyMs").value(20.2))
                .andExpect(jsonPath("$.thresholdBreached").value(false));
    }

    private EmbeddingMatch<TextSegment> match(String text, double score, Long conversationId) {
        Metadata metadata = new Metadata();
        metadata.put("conversationId", String.valueOf(conversationId));
        TextSegment segment = TextSegment.from(text, metadata);
        return new EmbeddingMatch<>(score, "id-1", Embedding.from(new float[]{1.0f, 0.0f}), segment);
    }
}
