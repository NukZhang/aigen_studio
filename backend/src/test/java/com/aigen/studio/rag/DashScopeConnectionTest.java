package com.aigen.studio.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashscope-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "qdrant.enabled=false"
})
class DashScopeConnectionTest {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void chatModelBeanIsAvailable() {
        assertNotNull(chatModel);
    }

    @Test
    void embeddingModelBeanIsAvailable() {
        assertNotNull(embeddingModel);
    }

    @Test
    void testChatModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DASHSCOPE_API_KEY not set, skipping live API test");

        String response = chatModel.generate("你好，请介绍一下自己（20字以内）");
        assertNotNull(response);
        assertFalse(response.isBlank());
    }

    @Test
    void testEmbeddingModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DASHSCOPE_API_KEY not set, skipping live API test");

        Embedding embedding = embeddingModel.embed("测试文本向量化").content();
        assertNotNull(embedding);
        assertEquals(1536, embedding.dimension());
    }
}
