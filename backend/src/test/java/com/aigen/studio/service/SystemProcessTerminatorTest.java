package com.aigen.studio.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;

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

    private void waitForExit(ProcessHandle handle, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (handle.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
    }
}
