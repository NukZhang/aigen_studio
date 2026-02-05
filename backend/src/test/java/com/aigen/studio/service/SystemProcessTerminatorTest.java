package com.aigen.studio.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemProcessTerminatorTest {

    @Test
    void terminateStopsDescendantProcesses() throws Exception {
        Process process = new ProcessBuilder(
                "sh",
                "-c",
                "sleep 300 & child=$!; echo $child; wait"
        ).start();

        long childPid;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            childPid = Long.parseLong(line.trim());
        }

        SystemProcessTerminator terminator = new SystemProcessTerminator();
        Optional<ProcessHandle> childHandle = ProcessHandle.of(childPid);

        assertTrue(process.isAlive());
        assertTrue(childHandle.isPresent() && childHandle.get().isAlive());

        try {
            assertTrue(terminator.terminate(process.pid()));
            waitForExit(process.toHandle(), Duration.ofSeconds(5));
            assertFalse(process.isAlive());
            assertFalse(childHandle.get().isAlive());
        } finally {
            childHandle.ifPresent(handle -> {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void terminateByPortStopsListeningProcess() throws Exception {
        Assumptions.assumeTrue(isCommandAvailable("lsof"));
        Assumptions.assumeTrue(isCommandAvailable("python3"));

        int port = findAvailablePort();
        Process process = new ProcessBuilder("python3", "-m", "http.server", String.valueOf(port))
                .redirectErrorStream(true)
                .start();

        SystemProcessTerminator terminator = new SystemProcessTerminator();
        try {
            assertTrue(waitForPort(port, Duration.ofSeconds(5)));
            assertTrue(terminator.terminateByPort(port));
            waitForExit(process.toHandle(), Duration.ofSeconds(5));
            assertFalse(process.isAlive());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void waitForExit(ProcessHandle handle, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (handle.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }

    private boolean waitForPort(int port, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (Exception ignored) {
                Thread.sleep(50);
            }
        }
        return false;
    }

    private int findAvailablePort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            return serverSocket.getLocalPort();
        }
    }

    private boolean isCommandAvailable(String command) throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "command -v " + command)
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        return exitCode == 0;
    }
}
