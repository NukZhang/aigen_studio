package com.aigen.studio.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

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
}
