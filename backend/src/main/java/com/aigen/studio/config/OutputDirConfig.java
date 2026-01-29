package com.aigen.studio.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class OutputDirConfig {

    @Value("${iflow.sdk.output-dir:./output}")
    private String outputDir;

    @PostConstruct
    public void initOutputDirectory() {
        try {
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                log.info("Created output directory: {}", outputPath.toAbsolutePath());
            } else {
                log.info("Output directory already exists: {}", outputPath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to create output directory: {}", outputDir, e);
        }
    }
}