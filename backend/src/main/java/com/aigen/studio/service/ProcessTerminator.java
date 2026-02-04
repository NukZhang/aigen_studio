package com.aigen.studio.service;

public interface ProcessTerminator {
    boolean terminate(long pid);

    boolean isAlive(long pid);
}
