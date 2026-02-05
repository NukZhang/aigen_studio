package com.aigen.studio.service;

public interface ProcessTerminator {
    boolean terminate(long pid);

    boolean terminateByPort(int port);

    boolean isAlive(long pid);
}
