package com.aigen.studio.service;

import java.io.IOException;

public interface ProcessLauncher {
    Process start(ProcessBuilder builder) throws IOException;
}
