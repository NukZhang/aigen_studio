package com.aigen.studio.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchemaCleanupConfigTest {

    @Test
    void cleanupUpdatesConversationStageConstraintForReadyToStart() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cleanup-test-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("CREATE TABLE conversations (id BIGINT PRIMARY KEY, stage VARCHAR(255))");
        jdbcTemplate.execute(
                "ALTER TABLE conversations ADD CONSTRAINT CONSTRAINT_STAGE " +
                        "CHECK (stage IN ('NEED_INPUT', 'UNDERSTANDING', 'UNDERSTANDING_CONFIRMED', " +
                        "'CODE_GENERATING', 'SERVICE_STARTING', 'PREVIEWING', 'COMPLETED', 'FAILED'))"
        );

        SchemaCleanupConfig config = new SchemaCleanupConfig(jdbcTemplate);
        config.cleanup();

        jdbcTemplate.update("INSERT INTO conversations (id, stage) VALUES (1, 'NEED_INPUT')");

        assertDoesNotThrow(() ->
                jdbcTemplate.update("UPDATE conversations SET stage='READY_TO_START' WHERE id=1")
        );
    }
}
