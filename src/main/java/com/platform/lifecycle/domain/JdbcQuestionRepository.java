package com.platform.lifecycle.domain;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link QuestionRepository}. Enum columns are bound as {@link Types#OTHER} so Postgres
 * coerces the string label to {@code question_state}. Only inserts are exposed; {@code question_events}
 * has no update or delete path here, and a database trigger rejects mutations as well.
 */
@Repository
public class JdbcQuestionRepository implements QuestionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcQuestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertPostedQuestion(
            UUID id, UUID studentId, String subject, String title, String body, Instant deadlineAt) {
        jdbc.update("""
                INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)
                VALUES (:id, :studentId, :subject, :title, :body, 'POSTED', :deadlineAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("studentId", studentId)
                        .addValue("subject", subject)
                        .addValue("title", title)
                        .addValue("body", body)
                        .addValue("deadlineAt", OffsetDateTime.ofInstant(deadlineAt, ZoneOffset.UTC)));
    }

    @Override
    public void appendEvent(
            UUID eventId, UUID questionId, String eventType, String fromState, String toState, String payloadJson) {
        jdbc.update("""
                INSERT INTO question_events (id, question_id, event_type, from_state, to_state, payload)
                VALUES (:id, :questionId, :eventType, :fromState, :toState, CAST(:payload AS jsonb))
                """,
                new MapSqlParameterSource()
                        .addValue("id", eventId)
                        .addValue("questionId", questionId)
                        .addValue("eventType", eventType)
                        .addValue("fromState", fromState, Types.OTHER)
                        .addValue("toState", toState, Types.OTHER)
                        .addValue("payload", payloadJson));
    }
}
