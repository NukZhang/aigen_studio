package com.aigen.studio.service;

import com.aigen.studio.dto.PreviewStatusDTO;
import com.aigen.studio.entity.Conversation;
import com.aigen.studio.entity.ConversationStage;
import com.aigen.studio.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final String PREVIEW_BASE_PATH = "/__preview__/";
    private static final String FRONTEND_ENTRY_MISSING_MESSAGE = "Frontend entry not found: index.html";
    private static final String BACKEND_NOT_READY_MESSAGE_PREFIX = "Backend not ready";
    private static final String ROOT_PATH_NOT_READY_MESSAGE = "Conversation generated code path is not ready";

    private final PreviewConfigResolver previewConfigResolver;
    private final ConversationRepository conversationRepository;
    private final ProcessLauncher processLauncher;
    private final PreviewScriptService previewScriptService;
    private final ProcessTerminator processTerminator;

    @Value("${preview.backend-ready-timeout-ms:45000}")
    private long backendReadyTimeoutMs;

    @Value("${preview.backend-ready-check-interval-ms:200}")
    private long backendReadyCheckIntervalMs;

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

            stopProcessesByPort(config);
            stopOrphanedProcesses(conversationId);

            Process frontendProcess = null;
            String frontendMessage = null;
            Process backendProcess = null;
            String backendMessage = null;

            Path backendDir = rootPath.resolve("backend");
            if (Files.isDirectory(backendDir)) {
                backendProcess = startBackend(backendDir, config.backendPort(), conversationId);
                if (!waitForBackendReady(config.backendPort(), backendProcess)) {
                    backendMessage = BACKEND_NOT_READY_MESSAGE_PREFIX + " on port " + config.backendPort();
                    log.warn(
                            "Backend readiness probe timeout for conversation {} on port {} after {} ms",
                            conversationId,
                            config.backendPort(),
                            Math.max(backendReadyTimeoutMs, 0)
                    );
                }
            }

            Path frontendDir = rootPath.resolve("frontend");
            if (Files.isDirectory(frontendDir)) {
                FrontendStartResult frontendResult = startFrontend(
                        frontendDir,
                        config.frontendPort(),
                        config.backendPort(),
                        conversationId
                );
                frontendProcess = frontendResult.process();
                frontendMessage = frontendResult.message();
            }

            activeSession = new PreviewSession(conversationId, frontendProcess, backendProcess, config);

            updateConversationAfterStart(conversation, frontendProcess, backendProcess, config);

            return buildStatus(
                    conversationId,
                    config,
                    frontendProcess,
                    backendProcess,
                    mergeMessages(frontendMessage, backendMessage)
            );
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
            if (config == null) {
                config = previewConfigResolver.resolve(resolveRoot(conversation));
            }

            stopOrphanedProcesses(conversationId);
            stopProcessesByPort(config);

            conversation.setStage(ConversationStage.READY_TO_START);
            conversation.setServiceStatus("STOPPED");
            conversation.setPreviewUrl(null);
            conversationRepository.save(conversation);

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
            Process frontend = null;
            Process backend = null;
            if (activeSession != null && activeSession.conversationId.equals(conversationId)) {
                PreviewConfig config = activeSession.config;
                frontend = activeSession.frontendProcess;
                backend = activeSession.backendProcess;
                return buildStatus(conversationId, config, frontend, backend, null);
            }

            Path rootPath = resolveRootIfReady(conversation);
            if (rootPath == null) {
                return buildNotReadyStatus(conversationId);
            }
            PreviewConfig config = previewConfigResolver.resolve(rootPath);
            return buildStatus(conversationId, config, frontend, backend, null);
        }
    }

    private FrontendStartResult startFrontend(Path frontendDir, int port, int backendPort, Long conversationId) {
        if (!hasFrontendEntry(frontendDir)) {
            log.warn("Frontend entry not found at {}", frontendDir.resolve("index.html"));
            return new FrontendStartResult(null, FRONTEND_ENTRY_MISSING_MESSAGE);
        }
        Path scriptPath = previewScriptService.ensureFrontendStartScript(frontendDir);
        previewScriptService.ensureFrontendRouterBase(frontendDir);
        previewScriptService.ensureFrontendApiProxyTarget(frontendDir, backendPort);
        previewScriptService.ensureFrontendApiBasePath(frontendDir, "/subapi");
        ensureFrontendDependencies(frontendDir);
        List<String> command = buildFrontendCommand(scriptPath, port);
        Path logFile = getLogFile(conversationId, "frontend");
        Process process = startProcess(frontendDir, command, conversationId, "Frontend", logFile);
        writePidFile(conversationId, "frontend", process);
        return new FrontendStartResult(process, null);
    }

    private Process startBackend(Path backendDir, int port, Long conversationId) {
        // 尝试查找主类
        String mainClass = findMainClass(backendDir);
        
        List<String> command;
        if (mainClass != null && !mainClass.isEmpty()) {
            command = List.of(
                    "mvn", "spring-boot:run", 
                    "-Dspring-boot.run.mainClass=" + mainClass,
                    "-Dspring-boot.run.arguments=--server.port=" + port
            );
        } else {
            // 如果找不到主类，使用默认命令
            command = List.of(
                    "mvn", "spring-boot:run", "-Dspring-boot.run.arguments=--server.port=" + port
            );
        }
        
        Path logFile = getLogFile(conversationId, "backend");
        Process process = startProcess(backendDir, command, conversationId, "Backend", logFile);
        writePidFile(conversationId, "backend", process);
        return process;
    }

    /**
     * 查找Spring Boot主类
     */
    private String findMainClass(Path backendDir) {
        // 1. 先检查pom.xml中是否有mainClass配置
        Path pomFile = backendDir.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            try {
                String pomContent = Files.readString(pomFile);
                // 查找spring-boot-maven-plugin配置中的mainClass
                if (pomContent.contains("<mainClass>")) {
                    int start = pomContent.indexOf("<mainClass>") + 11;
                    int end = pomContent.indexOf("</mainClass>", start);
                    if (end > start) {
                        return pomContent.substring(start, end).trim();
                    }
                }
            } catch (IOException e) {
                log.error("Failed to read pom.xml", e);
            }
        }

        // 2. 查找src/main/java目录下的Application类
        Path javaDir = backendDir.resolve("src/main/java");
        if (Files.exists(javaDir)) {
            try {
                java.util.List<String> appClasses = Files.walk(javaDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("Application.java"))
                        .map(p -> javaDir.relativize(p))
                        .map(p -> p.toString().replace(".java", "").replace("/", "."))
                        .toList();
                
                if (!appClasses.isEmpty()) {
                    log.info("Found main class: {}", appClasses.get(0));
                    return appClasses.get(0);
                }
            } catch (IOException e) {
                log.error("Failed to find main class", e);
            }
        }

        return null;
    }

    private Path getLogFile(Long conversationId, String service) {
        // 在数据目录下创建日志文件
        Path dataDir = Paths.get("data").toAbsolutePath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }
        return dataDir.resolve("preview-" + conversationId + "-" + service + ".log");
    }

    private Path getPidFile(Long conversationId, String service) {
        Path dataDir = Paths.get("data").toAbsolutePath();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }
        return dataDir.resolve("preview-" + conversationId + "-" + service + ".pid");
    }

    private List<String> buildFrontendCommand(Path scriptPath, int port) {
        if (scriptPath != null && Files.exists(scriptPath)) {
            return List.of(
                    "sh", "scripts/start-preview.sh", String.valueOf(port), PREVIEW_BASE_PATH
            );
        }
        return List.of(
                "npm", "run", "dev", "--", "--port", String.valueOf(port), "--strictPort", "--base", PREVIEW_BASE_PATH
        );
    }

    private boolean hasFrontendEntry(Path frontendDir) {
        if (frontendDir == null || !Files.isDirectory(frontendDir)) {
            return false;
        }
        return Files.exists(frontendDir.resolve("index.html"));
    }

    private Process startProcess(Path workingDir, List<String> command, Long conversationId, String serviceName, Path logFile) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        // 设置环境变量，确保进程以当前用户运行
        Map<String, String> environment = builder.environment();
        // 继承父进程的 PATH 等关键环境变量
        String path = System.getenv("PATH");
        if (path != null) {
            environment.put("PATH", path);
        }
        // 设置 HOME 环境变量，确保 npm/node 能正确访问用户目录
        String home = System.getenv("HOME");
        if (home != null) {
            environment.put("HOME", home);
        }
        // 设置 USER 环境变量
        String user = System.getenv("USER");
        if (user != null) {
            environment.put("USER", user);
        }

        try {
            Process process = processLauncher.start(builder);

            // 启动日志读取线程，写入文件
            startLogReader(process, serviceName, logFile);

            return process;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start preview process", e);
        }
    }

    private Process startProcess(Path workingDir, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        // 设置环境变量，确保进程以当前用户运行
        Map<String, String> environment = builder.environment();
        // 继承父进程的 PATH 等关键环境变量
        String path = System.getenv("PATH");
        if (path != null) {
            environment.put("PATH", path);
        }
        // 设置 HOME 环境变量，确保 npm/node 能正确访问用户目录
        String home = System.getenv("HOME");
        if (home != null) {
            environment.put("HOME", home);
        }
        // 设置 USER 环境变量
        String user = System.getenv("USER");
        if (user != null) {
            environment.put("USER", user);
        }

        try {
            return processLauncher.start(builder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start process", e);
        }
    }

    /**
     * 启动日志读取线程，将日志写入文件
     */
    private void startLogReader(Process process, String serviceName, Path logFile) {
        Thread logReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"));
                 java.io.PrintWriter writer = new java.io.PrintWriter(
                         new java.io.FileWriter(logFile.toFile(), true), true)) {
                writer.println("=== " + serviceName + " Log Started at " + new java.util.Date() + " ===");
                String line;
                while ((line = reader.readLine()) != null) {
                    String logLine = "[" + new java.util.Date() + "] [" + serviceName + "] " + line;
                    log.info(logLine);
                    writer.println(logLine);
                }
            } catch (IOException e) {
                log.error("Error reading {} logs", serviceName, e);
            }
        }, "LogReader-" + serviceName);
        logReader.setDaemon(true);
        logReader.start();
    }

    private void ensureFrontendDependencies(Path frontendDir) {
        Path packageJson = frontendDir.resolve("package.json");
        Path nodeModules = frontendDir.resolve("node_modules");
        if (!Files.exists(packageJson) || Files.isDirectory(nodeModules)) {
            return;
        }

        log.info("Installing frontend dependencies in {}", frontendDir);
        Process process = startProcess(frontendDir, List.of("npm", "install"));
        waitForProcess(process, "npm install");
    }

    private void waitForProcess(Process process, String action) {
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(action + " failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(action + " interrupted", e);
        }
    }

    private boolean waitForBackendReady(int backendPort, Process backendProcess) {
        long timeoutMs = Math.max(backendReadyTimeoutMs, 0);
        long checkIntervalMs = Math.max(backendReadyCheckIntervalMs, 10);

        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadlineNanos) {
            if (isBackendHttpReady(backendPort)) {
                return true;
            }
            if (!isAlive(backendProcess)) {
                return false;
            }
            sleepQuietly(Math.min(checkIntervalMs, 1000));
        }
        return isBackendHttpReady(backendPort);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String mergeMessages(String first, String second) {
        boolean firstBlank = first == null || first.isBlank();
        boolean secondBlank = second == null || second.isBlank();
        if (firstBlank && secondBlank) {
            return null;
        }
        if (firstBlank) {
            return second;
        }
        if (secondBlank) {
            return first;
        }
        return first + "; " + second;
    }


    private PreviewStatusDTO buildStatus(Long conversationId, PreviewConfig config, Process frontend, Process backend, String message) {
        // 优先使用端口检测，因为进程对象在后端重启后会丢失
        boolean frontendRunning = isPortInUse(config.frontendPort()) || isAlive(frontend);
        boolean backendRunning = isBackendRunning(config.backendPort(), backend);
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

    private boolean isBackendRunning(int backendPort, Process backend) {
        // 如果当前会话持有后端进程对象，优先以该进程真实存活状态为准，避免被无关端口占用误判。
        if (backend != null) {
            return isAlive(backend);
        }
        return isBackendHttpReady(backendPort);
    }

    private boolean isBackendHttpReady(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(300);
            connection.setReadTimeout(300);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            return status >= 100 && status <= 599;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isAlive(Process process) {
        return process != null && process.isAlive();
    }

    /**
     * 检查指定端口是否被占用。
     * 通过尝试连接 IPv4/IPv6 回环地址，避免仅 IPv6 监听时误判端口未占用。
     */
    private boolean isPortInUse(int port) {
        if (canConnect("127.0.0.1", port)) {
            return true;
        }
        if (canConnect("::1", port)) {
            return true;
        }
        return false;
    }

    private boolean canConnect(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Conversation loadConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    private Path resolveRootIfReady(Conversation conversation) {
        return resolveRoot(conversation, true);
    }

    private Path resolveRoot(Conversation conversation) {
        return resolveRoot(conversation, false);
    }

    private Path resolveRoot(Conversation conversation, boolean allowNotReady) {
        String rootPath = conversation.getGeneratedCodePath();
        if (rootPath == null || rootPath.isBlank()) {
            if (allowNotReady) {
                return null;
            }
            throw new RuntimeException(ROOT_PATH_NOT_READY_MESSAGE);
        }
        Path path = Paths.get(rootPath).toAbsolutePath();
        // 规范化路径，处理 macOS 上的符号链接（/var/folders -> /private/var/folders）
        try {
            path = path.toRealPath();
        } catch (IOException e) {
            // 如果无法解析真实路径，使用 normalize 作为后备
            path = path.normalize();
        }
        return path;
    }

    private PreviewStatusDTO buildNotReadyStatus(Long conversationId) {
        return new PreviewStatusDTO(
                conversationId,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                ROOT_PATH_NOT_READY_MESSAGE
        );
    }

    private void stopSession(PreviewSession session) {
        stopProcess(session.frontendProcess);
        stopProcess(session.backendProcess);
    }

    private void stopProcess(Process process) {
        if (process != null && process.isAlive()) {
            try {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (RuntimeException e) {
                log.warn("Failed to destroy preview process", e);
            }
        }
    }

    private void stopOrphanedProcesses(Long conversationId) {
        stopProcessByPid(conversationId, "frontend");
        stopProcessByPid(conversationId, "backend");
    }

    private void stopProcessesByPort(PreviewConfig config) {
        if (config == null) {
            return;
        }
        processTerminator.terminateByPort(config.frontendPort());
        processTerminator.terminateByPort(config.backendPort());
    }

    private void stopProcessByPid(Long conversationId, String service) {
        Path pidFile = getPidFile(conversationId, service);
        if (!Files.exists(pidFile)) {
            return;
        }

        Long pid = readPid(pidFile);
        if (pid != null) {
            boolean terminated = processTerminator.terminate(pid);
            if (!terminated) {
                log.warn("Failed to terminate {} process for conversation {} (pid={})", service, conversationId, pid);
            }
        }

        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            log.warn("Failed to remove pid file {}", pidFile, e);
        }
    }

    private Long readPid(Path pidFile) {
        try {
            String raw = Files.readString(pidFile).trim();
            if (raw.isBlank()) {
                return null;
            }
            return Long.parseLong(raw);
        } catch (IOException | NumberFormatException e) {
            log.warn("Failed to read pid file {}", pidFile, e);
            return null;
        }
    }

    private void writePidFile(Long conversationId, String service, Process process) {
        if (process == null) {
            return;
        }
        Path pidFile = getPidFile(conversationId, service);
        try {
            Files.writeString(pidFile, String.valueOf(process.pid()));
        } catch (IOException e) {
            log.warn("Failed to write pid file {}", pidFile, e);
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

    private record FrontendStartResult(Process process, String message) {
    }
}
