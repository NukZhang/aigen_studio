package com.aigen.studio.service;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private final PreviewConfigResolver previewConfigResolver;
    private final ConversationRepository conversationRepository;
    private final ProcessLauncher processLauncher;

    private final Object lock = new Object();
    private PreviewSession activeSession;

    public PreviewStatusDTO startPreview(Long conversationId) {
        synchronized (lock) {
            if (activeSession != null && !activeSession.conversationId.equals(conversationId)) {
                stopSession(activeSession);
                activeSession = null;
            }

            Conversation conversation = loadConversation(conversationId);
            Path rootPath = resolveRoot(conversation);
            PreviewConfig config = previewConfigResolver.resolve(rootPath);

            if (activeSession != null && activeSession.conversationId.equals(conversationId)) {
                return buildStatus(conversationId, config, activeSession.frontendProcess, activeSession.backendProcess, null);
            }

            Process frontendProcess = null;
            Process backendProcess = null;

            Path frontendDir = rootPath.resolve("frontend");
            if (Files.isDirectory(frontendDir)) {
                frontendProcess = startFrontend(frontendDir, config.frontendPort());
            }

            Path backendDir = rootPath.resolve("backend");
            if (Files.isDirectory(backendDir)) {
                backendProcess = startBackend(backendDir, config.backendPort());
            }

            activeSession = new PreviewSession(conversationId, frontendProcess, backendProcess, config);

            updateConversationAfterStart(conversation, frontendProcess, backendProcess, config);

            return buildStatus(conversationId, config, frontendProcess, backendProcess, null);
        }
    }

    public PreviewStatusDTO stopPreview(Long conversationId) {
        synchronized (lock) {
            PreviewConfig config = null;
            Process frontend = null;
            Process backend = null;

            if (activeSession != null && activeSession.conversationId.equals(conversationId)) {
                config = activeSession.config;
                frontend = activeSession.frontendProcess;
                backend = activeSession.backendProcess;
                stopSession(activeSession);
                activeSession = null;
            }

            Conversation conversation = loadConversation(conversationId);
            conversation.setStage(ConversationStage.READY_TO_START);
            conversation.setServiceStatus("STOPPED");
            conversation.setPreviewUrl(null);
            conversationRepository.save(conversation);

            if (config == null) {
                config = previewConfigResolver.resolve(resolveRoot(conversation));
            }

            return buildStatus(conversationId, config, frontend, backend, "stopped");
        }
    }

    public PreviewStatusDTO restartPreview(Long conversationId) {
        stopPreview(conversationId);
        return startPreview(conversationId);
    }

    public PreviewStatusDTO getStatus(Long conversationId) {
        synchronized (lock) {
            Conversation conversation = loadConversation(conversationId);
            PreviewConfig config = previewConfigResolver.resolve(resolveRoot(conversation));
            Process frontend = null;
            Process backend = null;
            if (activeSession != null && activeSession.conversationId.equals(conversationId)) {
                frontend = activeSession.frontendProcess;
                backend = activeSession.backendProcess;
            }
            return buildStatus(conversationId, config, frontend, backend, null);
        }
    }

    private Process startFrontend(Path frontendDir, int port) {
        List<String> command = List.of(
                "npm", "run", "dev", "--", "--port", String.valueOf(port), "--strictPort"
        );
        return startProcess(frontendDir, command);
    }

    private Process startBackend(Path backendDir, int port) {
        List<String> command = List.of(
                "mvn", "spring-boot:run", "-Dserver.port=" + port
        );
        return startProcess(backendDir, command);
    }

    private Process startProcess(Path workingDir, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        try {
            return processLauncher.start(builder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start preview process", e);
        }
    }

    private PreviewStatusDTO buildStatus(Long conversationId, PreviewConfig config, Process frontend, Process backend, String message) {
        boolean frontendRunning = isAlive(frontend);
        boolean backendRunning = isAlive(backend);
        boolean running = frontendRunning || backendRunning;

        String frontendUrl = frontendRunning ? "http://localhost:" + config.frontendPort() : null;
        String backendUrl = backendRunning ? "http://localhost:" + config.backendPort() : null;

        return new PreviewStatusDTO(
                conversationId,
                running,
                frontendRunning,
                backendRunning,
                config.frontendPort(),
                config.backendPort(),
                frontendUrl,
                backendUrl,
                message
        );
    }

    private boolean isAlive(Process process) {
        return process != null && process.isAlive();
    }

    private Conversation loadConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    private Path resolveRoot(Conversation conversation) {
        String rootPath = conversation.getGeneratedCodePath();
        if (rootPath == null || rootPath.isBlank()) {
            throw new RuntimeException("Conversation generated code path is not ready");
        }
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }

    private void stopSession(PreviewSession session) {
        stopProcess(session.frontendProcess);
        stopProcess(session.backendProcess);
    }

    private void stopProcess(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private void updateConversationAfterStart(
            Conversation conversation,
            Process frontend,
            Process backend,
            PreviewConfig config
    ) {
        boolean running = isAlive(frontend) || isAlive(backend);
        if (running) {
            conversation.setStage(ConversationStage.PREVIEWING);
            conversation.setServiceStatus("PREVIEWING");
            if (isAlive(frontend)) {
                conversation.setPreviewUrl("http://localhost:" + config.frontendPort());
            } else {
                conversation.setPreviewUrl(null);
            }
        } else {
            conversation.setServiceStatus("STOPPED");
            conversation.setPreviewUrl(null);
        }
        conversationRepository.save(conversation);
    }

    private record PreviewSession(
            Long conversationId,
            Process frontendProcess,
            Process backendProcess,
            PreviewConfig config
    ) {
    }
}
