package com.aigen.studio.controller;

import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import com.aigen.studio.service.ProcessLauncher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preview-controller-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestProcessLauncher processLauncher;

    @BeforeEach
    void resetLauncher() {
        processLauncher.startedBuilders.clear();
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TestProcessLauncher testProcessLauncher() {
            return new TestProcessLauncher();
        }
    }

    @Test
    void startAndStopPreview(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3002\n  backendPort: 8081\n");

        Conversation conversation = createConversation(tmp);

        mockMvc.perform(post("/preview/conversation/{id}/start", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", is(true)))
                .andExpect(jsonPath("$.frontendRunning", is(true)))
                .andExpect(jsonPath("$.backendRunning", is(true)))
                .andExpect(jsonPath("$.frontendUrl", is("http://localhost:3002")))
                .andExpect(jsonPath("$.backendUrl", is("http://localhost:8081")));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.PREVIEWING, updated.getStage());

        mockMvc.perform(get("/preview/conversation/{id}/status", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", is(true)));

        mockMvc.perform(post("/preview/conversation/{id}/stop", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running", is(false)));

        Conversation stopped = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.READY_TO_START, stopped.getStage());
    }

    private Conversation createConversation(Path root) {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Preview Controller");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.READY_TO_START);
        conversation.setGeneratedCodePath(root.toString());
        conversation.setUnderstandingConfirmed(false);
        return conversationRepository.save(conversation);
    }

    static class TestProcessLauncher implements ProcessLauncher {
        private final List<ProcessBuilder> startedBuilders = new ArrayList<>();

        @Override
        public Process start(ProcessBuilder builder) {
            startedBuilders.add(builder);
            return new FakeProcess();
        }
    }

    static class FakeProcess extends Process {
        private boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
