package com.aigen.studio.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SchemaCleanupConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void cleanup() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS artifacts");
            jdbcTemplate.execute("DROP TABLE IF EXISTS generation_jobs");
            jdbcTemplate.execute("DROP TABLE IF EXISTS ir_documents");
            jdbcTemplate.execute("DROP TABLE IF EXISTS requirements");
            jdbcTemplate.execute("ALTER TABLE conversations DROP COLUMN IF EXISTS job_id");
            jdbcTemplate.execute("ALTER TABLE conversations DROP COLUMN IF EXISTS requirement_id");
            jdbcTemplate.execute("ALTER TABLE messages DROP COLUMN IF EXISTS requirement_id");
            log.info("Schema cleanup completed.");
        } catch (Exception e) {
            log.warn("Schema cleanup skipped: {}", e.getMessage());
        }
    }
}
