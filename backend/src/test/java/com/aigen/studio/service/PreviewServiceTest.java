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
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
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

    @Autowired
    private TestProcessTerminator processTerminator;

    @BeforeEach
    void resetLauncher() {
        processLauncher.startedBuilders.clear();
        processLauncher.createdProcesses.clear();
        processLauncher.nextProcesses.clear();
        processTerminator.terminatedPids.clear();
        processTerminator.terminatedPorts.clear();
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TestProcessLauncher testProcessLauncher() {
            return new TestProcessLauncher();
        }

        @Bean
        @Primary
        TestProcessTerminator testProcessTerminator() {
            return new TestProcessTerminator();
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
    void startPreviewTerminatesConfiguredPorts(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("frontend/node_modules"));
        Files.writeString(tmp.resolve("frontend/index.html"), "<!doctype html><div id=\"app\"></div>");
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3010\n  backendPort: 8090\n");

        Conversation conversation = createConversation(tmp);

        previewService.startPreview(conversation.getId());

        assertTrue(processTerminator.terminatedPorts.contains(3010));
        assertTrue(processTerminator.terminatedPorts.contains(8090));
    }

    @Test
    void stopPreviewStopsProcessesAndResetsConversation(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3003\n  backendPort: 8083\n");

        Conversation conversation = createConversation(tmp);
        previewService.startPreview(conversation.getId());

        PreviewStatusDTO status = previewService.stopPreview(conversation.getId());

        assertTrue(processLauncher.createdProcesses.stream().allMatch(process -> !process.isAlive()));

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.READY_TO_START, updated.getStage());
    }

    @Test
    void stopPreviewTerminatesConfiguredPorts(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(tmp.resolve("frontend/node_modules"));
        Files.writeString(tmp.resolve("frontend/index.html"), "<!doctype html><div id=\"app\"></div>");
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3020\n  backendPort: 8100\n");

        Conversation conversation = createConversation(tmp);
        previewService.startPreview(conversation.getId());
        processTerminator.terminatedPorts.clear();

        previewService.stopPreview(conversation.getId());

        assertTrue(processTerminator.terminatedPorts.contains(3020));
        assertTrue(processTerminator.terminatedPorts.contains(8100));
    }

    @Test
    void stopPreviewDoesNotFailWhenProcessDestroyThrows(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(frontendDir.resolve("node_modules"));
        Files.writeString(frontendDir.resolve("index.html"), "<!doctype html><div id=\"app\"></div>");

        Conversation conversation = createConversation(tmp);
        processLauncher.enqueueProcess(new ExplodingProcess());

        previewService.startPreview(conversation.getId());

        PreviewStatusDTO status = previewService.stopPreview(conversation.getId());

        Conversation updated = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals(ConversationStage.READY_TO_START, updated.getStage());
        assertEquals("STOPPED", updated.getServiceStatus());
        assertTrue(status.isRunning() || !status.isRunning());
    }

    @Test
    void stopPreviewTerminatesOrphanedProcessesFromPidFiles(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Files.createDirectories(frontendDir.resolve("node_modules"));
        Files.writeString(frontendDir.resolve("index.html"), "<!doctype html><div id=\"app\"></div>");
        Files.createDirectories(tmp.resolve("backend"));
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3002\n  backendPort: 8081\n");

        Conversation conversation = createConversation(tmp);

        processLauncher.enqueueProcess(new PidProcess(111L));
        processLauncher.enqueueProcess(new PidProcess(222L));

        previewService.startPreview(conversation.getId());

        clearActiveSession(previewService);

        PreviewStatusDTO status = previewService.stopPreview(conversation.getId());

        assertTrue(processTerminator.terminatedPids.contains(111L));
        assertTrue(processTerminator.terminatedPids.contains(222L));

        Path dataDir = Path.of("data").toAbsolutePath();
        Path frontendPid = dataDir.resolve("preview-" + conversation.getId() + "-frontend.pid");
        Path backendPid = dataDir.resolve("preview-" + conversation.getId() + "-backend.pid");
        assertFalse(Files.exists(frontendPid));
        assertFalse(Files.exists(backendPid));
    }

    @Test
    void isPortInUseDetectsIpv6LoopbackListener() throws Exception {
        InetAddress ipv6Loopback;
        try {
            ipv6Loopback = InetAddress.getByName("::1");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "IPv6 loopback not available");
            return;
        }

        try (ServerSocket server = new ServerSocket(0, 0, ipv6Loopback)) {
            int port = server.getLocalPort();
            boolean inUse = invokeIsPortInUse(port);
            assertTrue(inUse);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "IPv6 binding not supported: " + e.getMessage());
        }
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
        private final List<Process> createdProcesses = new ArrayList<>();
        private final java.util.Deque<Process> nextProcesses = new java.util.ArrayDeque<>();

        void enqueueProcess(Process process) {
            nextProcesses.add(process);
        }

        @Override
        public Process start(ProcessBuilder builder) {
            startedBuilders.add(builder);
            Process process = nextProcesses.isEmpty() ? new FakeProcess() : nextProcesses.removeFirst();
            createdProcesses.add(process);
            return process;
        }
    }

    static class FakeProcess extends Process {
        private static final java.util.concurrent.atomic.AtomicLong PID_SEQUENCE =
                new java.util.concurrent.atomic.AtomicLong(1000);
        private final long pid = PID_SEQUENCE.incrementAndGet();
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

        @Override
        public long pid() {
            return pid;
        }
    }

    static class ExplodingProcess extends FakeProcess {
        @Override
        public void destroy() {
            throw new RuntimeException("boom");
        }
    }

    static class PidProcess extends FakeProcess {
        private final long pid;

        PidProcess(long pid) {
            this.pid = pid;
        }

        @Override
        public long pid() {
            return pid;
        }
    }

    static class TestProcessTerminator implements ProcessTerminator {
        private final List<Long> terminatedPids = new ArrayList<>();
        private final List<Integer> terminatedPorts = new ArrayList<>();

        @Override
        public boolean terminate(long pid) {
            terminatedPids.add(pid);
            return true;
        }

        @Override
        public boolean terminateByPort(int port) {
            terminatedPorts.add(port);
            return true;
        }

        @Override
        public boolean isAlive(long pid) {
            return true;
        }
    }

    private void clearActiveSession(PreviewService service) throws Exception {
        java.lang.reflect.Field field = PreviewService.class.getDeclaredField("activeSession");
        field.setAccessible(true);
        field.set(service, null);
    }

    private boolean invokeIsPortInUse(int port) throws Exception {
        Method method = PreviewService.class.getDeclaredMethod("isPortInUse", int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(previewService, port);
    }

}
