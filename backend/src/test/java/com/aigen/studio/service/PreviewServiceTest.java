package com.aigen.studio.service;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preview-service-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PreviewServiceTest {

    @Autowired
    private PreviewService previewService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestProcessLauncher processLauncher;

    @BeforeEach
    void resetLauncher() {
        processLauncher.startedBuilders.clear();
        processLauncher.createdProcesses.clear();
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
    void startPreviewStartsExistingServicesAndUpdatesConversation(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("frontend/node_modules"));
        Files.writeString(tmp.resolve("frontend/index.html"), "<!doctype html><div id=\"app\"></div>");
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3002\n  backendPort: 8081\n");

        Conversation conversation = createConversation(tmp);

        PreviewStatusDTO status = previewService.startPreview(conversation.getId());

        assertTrue(status.isRunning());
        assertTrue(status.isFrontendRunning());
        assertTrue(status.isBackendRunning());
        assertEquals(3002, status.getFrontendPort());
        assertEquals(8081, status.getBackendPort());
        assertEquals("http://localhost:3002", status.getFrontendUrl());
        assertEquals("http://localhost:8081", status.getBackendUrl());
        assertEquals(2, processLauncher.startedBuilders.size());
        assertEquals(List.of("sh", "scripts/start-preview.sh", "3002", "/__preview__/"),
                processLauncher.startedBuilders.get(0).command());

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.PREVIEWING, updated.getStage());
        assertEquals("http://localhost:3002", updated.getPreviewUrl());
    }

    @Test
    void startPreviewSkipsMissingBackend(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("frontend/node_modules"));
        Files.writeString(tmp.resolve("frontend/index.html"), "<!doctype html><div id=\"app\"></div>");
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3002\n  backendPort: 8082\n");

        Conversation conversation = createConversation(tmp);

        PreviewStatusDTO status = previewService.startPreview(conversation.getId());

        assertTrue(status.isRunning());
        assertTrue(status.isFrontendRunning());
        assertFalse(status.isBackendRunning());
        assertEquals(1, processLauncher.startedBuilders.size());
        assertEquals(List.of("sh", "scripts/start-preview.sh", "3002", "/__preview__/"),
                processLauncher.startedBuilders.get(0).command());
    }

    @Test
    void startPreviewSkipsFrontendWhenEntryMissing(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(frontendDir.resolve("node_modules"));
        Files.writeString(frontendDir.resolve("package.json"), "{}");

        Conversation conversation = createConversation(tmp);

        PreviewStatusDTO status = previewService.startPreview(conversation.getId());

        assertFalse(status.isRunning());
        assertFalse(status.isFrontendRunning());
        assertEquals(0, processLauncher.startedBuilders.size());
        assertTrue(status.getMessage() != null && status.getMessage().contains("index.html"));
    }

    @Test
    void startPreviewInstallsFrontendDependenciesWhenMissing(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Files.writeString(frontendDir.resolve("package.json"), "{}");
        Files.writeString(frontendDir.resolve("index.html"), "<!doctype html><div id=\"app\"></div>");

        Conversation conversation = createConversation(tmp);

        PreviewStatusDTO status = previewService.startPreview(conversation.getId());

        assertTrue(status.isRunning());
        assertEquals(2, processLauncher.startedBuilders.size());
        assertEquals(List.of("npm", "install"), processLauncher.startedBuilders.get(0).command());
        assertEquals(List.of(
                "sh", "scripts/start-preview.sh", String.valueOf(status.getFrontendPort()), "/__preview__/"
        ), processLauncher.startedBuilders.get(1).command());
    }

    @Test
    void stopPreviewStopsProcessesAndResetsConversation(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3003\n  backendPort: 8083\n");

        Conversation conversation = createConversation(tmp);
        previewService.startPreview(conversation.getId());

        PreviewStatusDTO status = previewService.stopPreview(conversation.getId());

        assertFalse(status.isRunning());
        assertTrue(processLauncher.createdProcesses.stream().allMatch(process -> !process.isAlive()));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.READY_TO_START, updated.getStage());
    }

    private Conversation createConversation(Path root) {
        Conversation conversation = new Conversation();
        conversation.setProjectName("Preview Test");
        conversation.setStatus(Conversation.ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.READY_TO_START);
        conversation.setGeneratedCodePath(root.toString());
        conversation.setUnderstandingConfirmed(false);
        return conversationRepository.save(conversation);
    }

    static class TestProcessLauncher implements ProcessLauncher {
        private final List<ProcessBuilder> startedBuilders = new ArrayList<>();
        private final List<FakeProcess> createdProcesses = new ArrayList<>();

        @Override
        public Process start(ProcessBuilder builder) {
            startedBuilders.add(builder);
            FakeProcess process = new FakeProcess();
            createdProcesses.add(process);
            return process;
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
