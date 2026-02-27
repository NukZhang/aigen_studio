package com.aigen.studio.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    private final VectorStoreService vectorStoreService;
    private final ChatLanguageModel chatLanguageModel;
    private final Map<SearchCacheKey, SearchCacheValue> searchCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheEvictions = new AtomicLong();
    private final AtomicLong lastAlertLoggedAtMillis = new AtomicLong(0L);
    private final AtomicLong totalSearches = new AtomicLong();
    private final DoubleAdder totalSearchLatencyMs = new DoubleAdder();
    private final DoubleAccumulator maxSearchLatencyMs = new DoubleAccumulator(Double::max, 0.0d);
    private final ArrayDeque<Double> searchLatencySamplesMs = new ArrayDeque<>();
    private final Object latencySamplesLock = new Object();

    @Value("${aigen.rag.search-cache-enabled:true}")
    private boolean searchCacheEnabled = true;

    @Value("${aigen.rag.search-cache-ttl-seconds:120}")
    private long searchCacheTtlSeconds = 120;

    @Value("${aigen.rag.search-cache-max-size:500}")
    private int searchCacheMaxSize = 500;

    @Value("${aigen.rag.alert-enabled:true}")
    private boolean alertEnabled = true;

    @Value("${aigen.rag.alert-min-requests:20}")
    private long alertMinRequests = 20L;

    @Value("${aigen.rag.alert-max-miss-rate:0.60}")
    private double alertMaxMissRate = 0.60d;

    @Value("${aigen.rag.alert-log-cooldown-seconds:300}")
    private long alertLogCooldownSeconds = 300L;

    @Value("${aigen.rag.perf-latency-threshold-ms:100}")
    private double perfLatencyThresholdMs = 100.0d;

    @Value("${aigen.rag.perf-sample-size:200}")
    private int perfSampleSize = 200;

    @Value("${aigen.rag.perf-min-samples:30}")
    private int perfMinSamples = 30;

    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank() || maxResults <= 0) {
            return List.of();
        }

        long startedAtNanos = System.nanoTime();
        try {
            if (!searchCacheEnabled) {
                cacheMisses.incrementAndGet();
                List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(query, maxResults);
                maybeLogCacheAlert();
                return matches;
            }

            long now = System.currentTimeMillis();
            SearchCacheKey key = new SearchCacheKey(normalizedQuery, maxResults);
            SearchCacheValue cached = searchCache.get(key);
            if (cached != null && cached.expireAtMillis() > now) {
                cacheHits.incrementAndGet();
                maybeLogCacheAlert();
                return cached.matches();
            }

            if (cached != null) {
                searchCache.remove(key);
                cacheEvictions.incrementAndGet();
            }

            cacheMisses.incrementAndGet();
            List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(query, maxResults);
            putSearchCache(key, matches, now);
            maybeLogCacheAlert();
            return matches;
        } finally {
            recordSearchLatencyMs((System.nanoTime() - startedAtNanos) / 1_000_000.0d);
        }
    }

    public String generateWithRAG(String query, Long conversationId) {
        List<EmbeddingMatch<TextSegment>> matches = search(query, 5).stream()
                .filter(match -> matchesConversation(match, conversationId))
                .collect(Collectors.toList());

        String context = buildContext(matches);
        String prompt = """
                基于以下上下文回答问题：

                ## 上下文
                %s

                ## 问题
                %s

                请基于上下文提供准确、详细的回答。如果上下文中没有相关信息，请明确说明。
                """.formatted(context, query);

        log.info("Generating RAG response, matched segments={}", matches.size());
        return chatLanguageModel.generate(prompt);
    }

    public void invalidateSearchCache() {
        if (!searchCache.isEmpty()) {
            searchCache.clear();
            log.info("RAG search cache invalidated");
        }
    }

    public CacheStats getCacheStats() {
        return new CacheStats(
                cacheHits.get(),
                cacheMisses.get(),
                cacheEvictions.get(),
                searchCache.size()
        );
    }

    public CacheAlertStatus getCacheAlertStatus() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long totalRequests = hits + misses;
        double missRate = totalRequests == 0 ? 0.0d : (double) misses / totalRequests;
        long minRequests = Math.max(0L, alertMinRequests);
        double maxMissRate = Math.max(0.0d, alertMaxMissRate);
        boolean triggered = alertEnabled && totalRequests >= minRequests && missRate > maxMissRate;
        String message;
        if (!alertEnabled) {
            message = "cache alert disabled";
        } else if (totalRequests < minRequests) {
            message = "insufficient requests for alert evaluation";
        } else if (triggered) {
            message = "cache miss rate exceeded threshold";
        } else {
            message = "cache alert healthy";
        }
        return new CacheAlertStatus(
                alertEnabled,
                triggered,
                totalRequests,
                missRate,
                hits,
                misses,
                cacheEvictions.get(),
                searchCache.size(),
                minRequests,
                maxMissRate,
                message
        );
    }

    public PerformanceStats getPerformanceStats() {
        long total = totalSearches.get();
        double averageLatency = total == 0 ? 0.0d : totalSearchLatencyMs.sum() / total;

        List<Double> latencySnapshot;
        synchronized (latencySamplesLock) {
            latencySnapshot = new ArrayList<>(searchLatencySamplesMs);
        }
        double p95Latency = percentile(latencySnapshot, 95);
        int minSamples = Math.max(1, perfMinSamples);
        boolean thresholdBreached = latencySnapshot.size() >= minSamples && p95Latency > Math.max(0.0d, perfLatencyThresholdMs);

        return new PerformanceStats(
                total,
                averageLatency,
                p95Latency,
                maxSearchLatencyMs.get(),
                Math.max(0.0d, perfLatencyThresholdMs),
                minSamples,
                latencySnapshot.size(),
                thresholdBreached
        );
    }

    private boolean matchesConversation(EmbeddingMatch<TextSegment> match, Long conversationId) {
        if (conversationId == null) {
            return true;
        }
        String value = match.embedded().metadata().get("conversationId");
        return Objects.equals(String.valueOf(conversationId), value);
    }

    private String buildContext(List<EmbeddingMatch<TextSegment>> matches) {
        if (matches == null || matches.isEmpty()) {
            return "暂无相关上下文";
        }
        return matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private void putSearchCache(SearchCacheKey key, List<EmbeddingMatch<TextSegment>> matches, long nowMillis) {
        if (!searchCacheEnabled || searchCacheMaxSize <= 0) {
            return;
        }

        if (searchCache.size() >= searchCacheMaxSize) {
            evictOldestEntry();
        }

        long ttlMillis = Math.max(1, searchCacheTtlSeconds) * 1000L;
        searchCache.put(key, new SearchCacheValue(List.copyOf(matches), nowMillis, nowMillis + ttlMillis));
    }

    private void evictOldestEntry() {
        SearchCacheKey oldestKey = null;
        long oldestCreatedAt = Long.MAX_VALUE;
        for (Map.Entry<SearchCacheKey, SearchCacheValue> entry : searchCache.entrySet()) {
            if (entry.getValue().createdAtMillis() < oldestCreatedAt) {
                oldestCreatedAt = entry.getValue().createdAtMillis();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null && searchCache.remove(oldestKey) != null) {
            cacheEvictions.incrementAndGet();
        }
    }

    private void maybeLogCacheAlert() {
        CacheAlertStatus status = getCacheAlertStatus();
        if (!status.triggered()) {
            return;
        }

        long cooldownMillis = Math.max(0L, alertLogCooldownSeconds) * 1000L;
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastAlertLoggedAtMillis.get();
            if (cooldownMillis > 0 && now - previous < cooldownMillis) {
                return;
            }
            if (lastAlertLoggedAtMillis.compareAndSet(previous, now)) {
                log.warn("RAG cache alert triggered: missRate={}, threshold={}, totalRequests={}, hits={}, misses={}, evictions={}, size={}",
                        status.missRate(), status.maxMissRate(), status.totalRequests(),
                        status.hits(), status.misses(), status.evictions(), status.size());
                return;
            }
        }
    }

    private void recordSearchLatencyMs(double latencyMs) {
        double safeLatency = Math.max(0.0d, latencyMs);
        totalSearches.incrementAndGet();
        totalSearchLatencyMs.add(safeLatency);
        maxSearchLatencyMs.accumulate(safeLatency);

        int sampleLimit = Math.max(1, perfSampleSize);
        synchronized (latencySamplesLock) {
            searchLatencySamplesMs.addLast(safeLatency);
            while (searchLatencySamplesMs.size() > sampleLimit) {
                searchLatencySamplesMs.removeFirst();
            }
        }
    }

    private double percentile(List<Double> values, int percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int index = (int) Math.ceil(percentile / 100.0d * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    private record SearchCacheKey(String query, int maxResults) {
    }

    private record SearchCacheValue(
            List<EmbeddingMatch<TextSegment>> matches,
            long createdAtMillis,
            long expireAtMillis
    ) {
    }

    public record CacheStats(long hits, long misses, long evictions, int size) {
    }

    public record CacheAlertStatus(
            boolean enabled,
            boolean triggered,
            long totalRequests,
            double missRate,
            long hits,
            long misses,
            long evictions,
            int size,
            long minRequests,
            double maxMissRate,
            String message
    ) {
    }

    public record PerformanceStats(
            long totalSearches,
            double avgLatencyMs,
            double p95LatencyMs,
            double maxLatencyMs,
            double latencyThresholdMs,
            int minSamples,
            int sampleCount,
            boolean thresholdBreached
    ) {
    }
}
