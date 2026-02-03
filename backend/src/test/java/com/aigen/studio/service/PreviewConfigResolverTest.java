package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PreviewConfigResolverTest {

    @Test
    void usesDefaultPortsWhenConfigMissing(@TempDir Path tmp) throws Exception {
        Object config = resolveConfig(tmp);

        assertEquals(3002, readPort(config, "frontendPort"));
        assertEquals(8081, readPort(config, "backendPort"));
    }

    @Test
    void readsPortsFromApplicationYaml(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3002\n  backendPort: 8081\n");

        Object config = resolveConfig(tmp);

        assertEquals(3002, readPort(config, "frontendPort"));
        assertEquals(8081, readPort(config, "backendPort"));
    }

    @Test
    void avoidsReservedPorts(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("application.yml"), "preview:\n  frontendPort: 3000\n  backendPort: 8080\n");

        Object config = resolveConfig(tmp);

        assertNotEquals(3000, readPort(config, "frontendPort"));
        assertNotEquals(8080, readPort(config, "backendPort"));
    }

    private Object resolveConfig(Path root) {
        try {
            Class<?> resolverClass = Class.forName("com.aigen.studio.service.PreviewConfigResolver");
            Object resolver = resolverClass.getDeclaredConstructor().newInstance();
            Method resolve = resolverClass.getMethod("resolve", Path.class);
            return resolve.invoke(resolver, root);
        } catch (Exception e) {
            fail("Failed to resolve preview config: " + e.getMessage());
            return null;
        }
    }

    private int readPort(Object config, String methodName) {
        try {
            Method method = config.getClass().getMethod(methodName);
            return ((Number) method.invoke(config)).intValue();
        } catch (Exception e) {
            fail("Failed to read port: " + e.getMessage());
            return -1;
        }
    }
}
