package com.aigen.studio.service;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultProcessLauncher implements ProcessLauncher {
    @Override
    public Process start(ProcessBuilder builder) throws IOException {
        return builder.start();
    }
}
