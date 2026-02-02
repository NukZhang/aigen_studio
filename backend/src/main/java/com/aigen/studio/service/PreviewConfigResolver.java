package com.aigen.studio.service;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Service
public class PreviewConfigResolver {

    private static final int DEFAULT_FRONTEND_PORT = 3001;
    private static final int DEFAULT_BACKEND_PORT = 8081;
    private static final int RESERVED_FRONTEND_PORT = 3000;
    private static final int RESERVED_BACKEND_PORT = 8080;

    public PreviewConfig resolve(Path root) {
        Path configPath = root.resolve("application.yml");
        if (!Files.exists(configPath)) {
            return new PreviewConfig(DEFAULT_FRONTEND_PORT, DEFAULT_BACKEND_PORT);
        }

        Properties properties = loadYaml(configPath);
        int frontendPort = parsePort(properties.getProperty("preview.frontendPort"), DEFAULT_FRONTEND_PORT);
        int backendPort = parsePort(properties.getProperty("preview.backendPort"), DEFAULT_BACKEND_PORT);

        frontendPort = avoidReserved(frontendPort, RESERVED_FRONTEND_PORT);
        backendPort = avoidReserved(backendPort, RESERVED_BACKEND_PORT);

        return new PreviewConfig(frontendPort, backendPort);
    }

    private Properties loadYaml(Path configPath) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(configPath.toFile()));
        Properties properties = yaml.getObject();
        return properties != null ? properties : new Properties();
    }

    private int parsePort(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int avoidReserved(int port, int reserved) {
        return port == reserved ? port + 1 : port;
    }
}
