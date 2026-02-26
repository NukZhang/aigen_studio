package com.aigen.studio.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(QdrantConfig.QdrantProperties.class)
public class QdrantConfig {

    @Bean
    @ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true")
    public QdrantClient qdrantClient(QdrantProperties properties) {
        log.info("Initializing Qdrant gRPC client at {}:{}", properties.getHost(), properties.getPort());
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(properties.getHost(), properties.getPort(), properties.isUseTls())
                        .build()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true")
    public EmbeddingStore<TextSegment> qdrantEmbeddingStore(QdrantProperties properties) {
        log.info("Using Qdrant collection: {}", properties.getCollectionName());
        return QdrantEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .collectionName(properties.getCollectionName())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "false", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        log.warn("Qdrant is disabled; fallback to in-memory embedding store.");
        return new InMemoryEmbeddingStore<>();
    }

    @Data
    @ConfigurationProperties(prefix = "qdrant")
    public static class QdrantProperties {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 6334;
        private boolean useTls = false;
        private String collectionName = "aigen_knowledge";
    }
}
