package com.platform.expertportal.domain;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link ClaimAttemptRepository}. The nullable {@code question_id} is bound through a
 * {@code CAST(... AS uuid)} so a skip (null) and a win are written the same way without a driver
 * null-type ambiguity.
 */
@Repository
public class JdbcClaimAttemptRepository implements ClaimAttemptRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcClaimAttemptRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID id, UUID expertId, String subject, String outcome, UUID questionId) {
        jdbc.update("""
                INSERT INTO claim_attempts (id, expert_id, subject, outcome, question_id)
                VALUES (:id, :expertId, :subject, :outcome, CAST(:questionId AS uuid))
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("expertId", expertId)
                        .addValue("subject", subject)
                        .addValue("outcome", outcome)
                        .addValue("questionId", questionId == null ? null : questionId.toString()));
    }
}
