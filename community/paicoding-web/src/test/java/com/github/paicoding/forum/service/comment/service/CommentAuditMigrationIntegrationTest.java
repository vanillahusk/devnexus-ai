package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.web.QuickForumApplication;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=true"
})
@EnabledIfSystemProperty(named = "comment.audit.migration.integration.enabled", matches = "true")
class CommentAuditMigrationIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MessageQueueService messageQueueService;

    @MockBean
    private MqOutboxService outboxService;

    @Test
    void shouldApplyAuditColumnIndexAndKeepHistoricalCommentsApproved() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment' AND COLUMN_NAME = 'audit_status'",
                Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment' AND INDEX_NAME = 'idx_comment_visible_tree'",
                Integer.class);
        Integer invalidHistoricalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE audit_status IS NULL OR audit_status NOT IN (0, 1, 2)",
                Integer.class);

        assertEquals(1, columnCount);
        assertEquals(5, indexCount);
        assertEquals(0, invalidHistoricalRows);
    }
}
