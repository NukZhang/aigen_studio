package com.aigen.studio.config;

import com.aigen.studio.entity.ConversationStage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SchemaCleanupConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void cleanup() {
        executeSafely("DROP TABLE IF EXISTS artifacts", "drop artifacts table");
        executeSafely("DROP TABLE IF EXISTS generation_jobs", "drop generation_jobs table");
        executeSafely("DROP TABLE IF EXISTS ir_documents", "drop ir_documents table");
        executeSafely("DROP TABLE IF EXISTS requirements", "drop requirements table");
        executeSafely("ALTER TABLE conversations DROP COLUMN IF EXISTS job_id", "drop conversations.job_id column");
        executeSafely("ALTER TABLE conversations DROP COLUMN IF EXISTS requirement_id", "drop conversations.requirement_id column");
        executeSafely("ALTER TABLE messages DROP COLUMN IF EXISTS requirement_id", "drop messages.requirement_id column");
        ensureConversationStageConstraint();
        log.info("Schema cleanup completed.");
    }

    void ensureConversationStageConstraint() {
        if (!isH2Database()) {
            return;
        }

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='CONVERSATIONS'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT tc.CONSTRAINT_NAME " +
                        "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                        "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc " +
                        "ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME " +
                        "WHERE tc.TABLE_NAME='CONVERSATIONS' " +
                        "AND cc.CHECK_CLAUSE LIKE '%\"STAGE\"%'",
                String.class
        );

        executeSafely("ALTER TABLE conversations DROP CONSTRAINT IF EXISTS CONV_STAGE_CHECK",
                "drop conversations stage check constraint");
        for (String constraint : constraints) {
            executeSafely("ALTER TABLE conversations DROP CONSTRAINT " + constraint,
                    "drop conversations constraint " + constraint);
        }

        String allowedStages = java.util.Arrays.stream(ConversationStage.values())
                .map(ConversationStage::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        executeSafely(
                "ALTER TABLE conversations ADD CONSTRAINT CONV_STAGE_CHECK CHECK (stage IN (" + allowedStages + "))",
                "add conversations stage check constraint"
        );
    }

    private void executeSafely(String sql, String action) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Schema cleanup skipped {}: {}", action, e.getMessage());
        }
    }

    private boolean isH2Database() {
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("h2");
        } catch (Exception e) {
            log.warn("Unable to determine database type: {}", e.getMessage());
            return false;
        }
    }
}
