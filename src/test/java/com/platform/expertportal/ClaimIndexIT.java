package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.support.PostgresContainerSupport;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The claim's candidate select (master-design 6.2) is served by the partial index
 * {@code idx_questions_claimable (subject, priority DESC, created_at ASC) WHERE state='CLAIMABLE'}.
 * We seed claimable rows and assert the planner's chosen plan names that index. {@code enable_seqscan}
 * is toggled off on the single connection used for the EXPLAIN and reset afterwards so the pooled
 * connection is left clean.
 */
@SpringBootTest
class ClaimIndexIT extends PostgresContainerSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void claimCandidateSelectIsServedByTheClaimableIndex() {
        String subject = "idx-" + UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            jdbc.update("INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                            + " VALUES (?, ?, ?, 'title', 'body', 'CLAIMABLE', ?)",
                    UUID.randomUUID(), UUID.randomUUID(), subject, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        }

        String candidateSelect = "EXPLAIN (FORMAT JSON) SELECT id FROM questions"
                + " WHERE state = 'CLAIMABLE' AND subject = '" + subject + "' AND deadline_at > now()"
                + " ORDER BY priority DESC, created_at ASC FOR UPDATE SKIP LOCKED LIMIT 1";

        String plan = jdbc.execute((java.sql.Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                String json;
                try (ResultSet rs = statement.executeQuery(candidateSelect)) {
                    rs.next();
                    json = rs.getString(1);
                }
                statement.execute("RESET enable_seqscan");
                return json;
            }
        });

        assertThat(plan).as("the claim sort is index-served").contains("idx_questions_claimable");
    }
}
