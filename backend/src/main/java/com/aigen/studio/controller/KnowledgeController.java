package com.aigen.studio.controller;

import com.aigen.studio.dto.KnowledgeIngestRequest;
import com.aigen.studio.rag.ConversationVectorService;
import com.aigen.studio.rag.DocumentIngestionService;
import com.aigen.studio.rag.RAGService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentIngestionService documentIngestionService;
    private final RAGService ragService;
    private final ConversationVectorService conversationVectorService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long conversationId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source != null && !source.isBlank()) {
            metadata.put("source", source);
        }
        if (conversationId != null) {
            metadata.put("conversationId", conversationId);
        }

        DocumentIngestionService.IngestionResult result =
                documentIngestionService.ingestMultipartFile(file, metadata);
        return ResponseEntity.ok(buildIngestionResponse(result));
    }

    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> ingestText(@RequestBody KnowledgeIngestRequest request) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        Map<String, Object> metadata = request.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getMetadata());

        DocumentIngestionService.IngestionResult result =
                documentIngestionService.ingestMarkdown(request.getContent(), metadata);
        return ResponseEntity.ok(buildIngestionResponse(result));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "5") int maxResults,
            @RequestParam(required = false) Long conversationId
    ) {
        List<EmbeddingMatch<TextSegment>> matches = conversationId == null
                ? ragService.search(query, maxResults)
                : conversationVectorService.searchConversationHistory(conversationId, query, maxResults);

        List<Map<String, Object>> response = matches.stream()
                .map(match -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("score", match.score());
                    row.put("text", match.embedded().text());
                    row.put("metadata", match.embedded().metadata().toMap());
                    return row;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> cacheStats() {
        RAGService.CacheStats stats = ragService.getCacheStats();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hits", stats.hits());
        payload.put("misses", stats.misses());
        payload.put("evictions", stats.evictions());
        payload.put("size", stats.size());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/cache/alerts")
    public ResponseEntity<Map<String, Object>> cacheAlerts() {
        RAGService.CacheAlertStatus status = ragService.getCacheAlertStatus();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", status.enabled());
        payload.put("triggered", status.triggered());
        payload.put("totalRequests", status.totalRequests());
        payload.put("missRate", status.missRate());
        payload.put("hits", status.hits());
        payload.put("misses", status.misses());
        payload.put("evictions", status.evictions());
        payload.put("size", status.size());
        payload.put("minRequests", status.minRequests());
        payload.put("maxMissRate", status.maxMissRate());
        payload.put("message", status.message());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/perf/stats")
    public ResponseEntity<Map<String, Object>> performanceStats() {
        RAGService.PerformanceStats stats = ragService.getPerformanceStats();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalSearches", stats.totalSearches());
        payload.put("avgLatencyMs", stats.avgLatencyMs());
        payload.put("p95LatencyMs", stats.p95LatencyMs());
        payload.put("maxLatencyMs", stats.maxLatencyMs());
        payload.put("latencyThresholdMs", stats.latencyThresholdMs());
        payload.put("minSamples", stats.minSamples());
        payload.put("sampleCount", stats.sampleCount());
        payload.put("thresholdBreached", stats.thresholdBreached());
        return ResponseEntity.ok(payload);
    }

    private Map<String, Object> buildIngestionResponse(DocumentIngestionService.IngestionResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("segmentCount", result.segmentCount());
        response.put("segmentIds", result.segmentIds());
        return response;
    }
}
