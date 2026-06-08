package com.platform.lifecycle.domain;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    @Override
    public Optional<QuestionSnapshot> find(UUID id) {
        return jdbc.query("SELECT state::text AS state, version FROM questions WHERE id = :id",
                        new MapSqlParameterSource("id", id),
                        (rs, rowNum) -> new QuestionSnapshot(
                                QuestionState.valueOf(rs.getString("state")), rs.getLong("version")))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Long> applyTransition(UUID id, QuestionState from, QuestionState to, long expectedVersion) {
        // Optimistic-concurrency guard (master-design 6.1, 8): the UPDATE matches only while the row is
        // still in `from` at `expectedVersion`. Concurrent attempts serialize on the row lock; once the
        // winner commits version + 1, every other attempt matches no row and returns empty. RETURNING
        // yields the new version for the single winner.
        List<Long> newVersion = jdbc.queryForList("""
                UPDATE questions
                SET state = :to, version = version + 1, updated_at = now()
                WHERE id = :id AND state = :from AND version = :expectedVersion
                RETURNING version
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("from", from.name(), Types.OTHER)
                        .addValue("to", to.name(), Types.OTHER)
                        .addValue("expectedVersion", expectedVersion),
                Long.class);
        return newVersion.stream().findFirst();
    }

    @Override
    public Optional<ClaimedRow> claimNextClaimable(String subject, UUID expertId, int leaseMinutes) {
        // Work-queue claim (master-design 6.2): the candidate CTE locks exactly one claimable row with
        // FOR UPDATE SKIP LOCKED, so two concurrent claimers never select the same row; the UPDATE flips
        // that row to CLAIMED with a fresh lease in the same statement. 0 rows -> nothing was claimable.
        // The candidate filter and sort are served by idx_questions_claimable (subject, priority DESC,
        // created_at ASC) WHERE state = 'CLAIMABLE'.
        List<ClaimedRow> claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM questions
                    WHERE state = 'CLAIMABLE' AND subject = :subject AND deadline_at > now()
                    ORDER BY priority DESC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE questions
                SET state = 'CLAIMED', claimed_by = :expertId, claimed_at = now(),
                    claim_expires_at = now() + make_interval(mins => :leaseMinutes), version = version + 1
                WHERE id IN (SELECT id FROM candidate)
                RETURNING id, subject, claimed_by, claim_expires_at, version
                """,
                new MapSqlParameterSource()
                        .addValue("subject", subject)
                        .addValue("expertId", expertId)
                        .addValue("leaseMinutes", leaseMinutes),
                (rs, rowNum) -> new ClaimedRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("subject"),
                        rs.getObject("claimed_by", UUID.class),
                        rs.getObject("claim_expires_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("version")));
        return claimed.stream().findFirst();
    }

    @Override
    public boolean startWork(UUID id, UUID expertId) {
        int rows = jdbc.update("""
                UPDATE questions
                SET state = 'IN_PROGRESS', version = version + 1, updated_at = now()
                WHERE id = :id AND state = 'CLAIMED' AND claimed_by = :expertId AND claim_expires_at > now()
                """,
                new MapSqlParameterSource().addValue("id", id).addValue("expertId", expertId));
        return rows > 0;
    }

    @Override
    public boolean submitIfOwned(UUID id, UUID expertId) {
        // Conditional submit (master-design 6.3): matches only an IN_PROGRESS question the caller still
        // owns under a live lease. 0 rows -> the claim was stale, so the caller must not deliver.
        int rows = jdbc.update("""
                UPDATE questions
                SET state = 'SUBMITTED', version = version + 1, updated_at = now()
                WHERE id = :id AND state = 'IN_PROGRESS' AND claimed_by = :expertId AND claim_expires_at > now()
                """,
                new MapSqlParameterSource().addValue("id", id).addValue("expertId", expertId));
        return rows > 0;
    }

    @Override
    public List<ExpiredLease> expireLeasedBatch(int batchSize) {
        // SLA sweep (master-design 6.4): lock a batch of overdue leases with FOR UPDATE SKIP LOCKED so
        // two sweepers take disjoint rows, and flip them to CLAIM_EXPIRED in the same statement. The CTE
        // keeps the pre-expiry state and subject for the audit row and the queue re-projection.
        return jdbc.query("""
                WITH expired AS (
                    SELECT id, state, subject FROM questions
                    WHERE state IN ('CLAIMED', 'IN_PROGRESS') AND claim_expires_at <= now()
                    ORDER BY claim_expires_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batch
                )
                UPDATE questions q
                SET state = 'CLAIM_EXPIRED', version = version + 1, updated_at = now()
                FROM expired e
                WHERE q.id = e.id
                RETURNING q.id, e.state::text AS from_state, e.subject AS subject, q.claimed_by, q.version
                """,
                new MapSqlParameterSource().addValue("batch", batchSize),
                (rs, rowNum) -> new ExpiredLease(
                        rs.getObject("id", UUID.class),
                        rs.getString("from_state"),
                        rs.getString("subject"),
                        rs.getObject("claimed_by", UUID.class),
                        rs.getLong("version")));
    }

    @Override
    public boolean reopenExpired(UUID id) {
        int rows = jdbc.update("""
                UPDATE questions
                SET state = 'CLAIMABLE', claimed_by = NULL, claimed_at = NULL, claim_expires_at = NULL,
                    version = version + 1, updated_at = now()
                WHERE id = :id AND state = 'CLAIM_EXPIRED'
                """,
                new MapSqlParameterSource("id", id));
        return rows > 0;
    }
}
