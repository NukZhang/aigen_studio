package com.aigen.studio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SystemProcessTerminator implements ProcessTerminator {
    @Override
    public boolean terminate(long pid) {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            return false;
        }
        ProcessHandle processHandle = handle.get();
        boolean terminated = terminateDescendants(processHandle);
        if (!processHandle.isAlive()) {
            return true;
        }
        boolean destroyed = processHandle.destroy();
        if (destroyed) {
            return true;
        }
        return processHandle.destroyForcibly() || terminated;
    }

    @Override
    public boolean terminateByPort(int port) {
        List<Long> pids = findPidsByPort(port);
        boolean terminated = false;
        long selfPid = ProcessHandle.current().pid();
        for (Long pid : pids) {
            if (pid == null || pid == selfPid) {
                continue;
            }
            terminated = terminate(pid) || terminated;
        }
        return terminated;
    }

    @Override
    public boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private boolean terminateDescendants(ProcessHandle processHandle) {
        java.util.List<ProcessHandle> descendants = processHandle.descendants().toList();
        boolean terminated = false;
        for (ProcessHandle descendant : descendants) {
            if (descendant.isAlive()) {
                descendant.destroy();
                terminated = true;
            }
        }
        for (ProcessHandle descendant : descendants) {
            if (descendant.isAlive()) {
                descendant.destroyForcibly();
                terminated = true;
            }
        }
        return terminated;
    }

    private List<Long> findPidsByPort(int port) {
        List<Long> pids = new ArrayList<>();
        ProcessBuilder builder = new ProcessBuilder(
                "lsof",
                "-n",
                "-P",
                "-iTCP:" + port,
                "-sTCP:LISTEN",
                "-t"
        );
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    try {
                        pids.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse pid from lsof output: {}", trimmed);
                    }
                }
            }
            process.waitFor();
        } catch (IOException e) {
            log.warn("Failed to execute lsof for port {}", port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for lsof on port {}", port, e);
        }
        return pids;
    }
}
