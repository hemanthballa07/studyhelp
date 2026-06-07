package com.platform.expertportal.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link ClaimableQueueRepository}. The add is an {@code INSERT ... ON CONFLICT DO
 * NOTHING} and the remove a {@code DELETE}, so both are idempotent under event redelivery.
 */
@Repository
public class JdbcClaimableQueueRepository implements ClaimableQueueRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcClaimableQueueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(UUID questionId, String subject) {
        jdbc.update("""
                INSERT INTO claimable_questions (question_id, subject)
                VALUES (:questionId, :subject)
                ON CONFLICT (question_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("questionId", questionId)
                        .addValue("subject", subject));
    }

    @Override
    public int remove(UUID questionId) {
        return jdbc.update("DELETE FROM claimable_questions WHERE question_id = :questionId",
                new MapSqlParameterSource("questionId", questionId));
    }

    @Override
    public List<ClaimableQuestionView> findBySubject(String subject, int limit) {
        return jdbc.query("""
                SELECT question_id, subject, routed_at FROM claimable_questions
                WHERE subject = :subject
                ORDER BY routed_at ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("subject", subject)
                        .addValue("limit", limit),
                (rs, rowNum) -> new ClaimableQuestionView(
                        rs.getObject("question_id", UUID.class),
                        rs.getString("subject"),
                        rs.getObject("routed_at", OffsetDateTime.class).toInstant()));
    }
}
