package com.platform.shared.outbox;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link OutboxStore}. Translates the camelCase {@link OutboxEvent} to and from the
 * snake_case {@code outbox} columns (master-design section 5). {@link #fetchUnpublished} uses
 * {@code FOR UPDATE SKIP LOCKED} so concurrent relays never grab the same row.
 */
@Repository
public class JdbcOutboxStore implements OutboxStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOutboxStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(OutboxEvent event) {
        jdbc.update("""
                INSERT INTO outbox (event_id, aggregate_id, aggregate_type, event_type, payload, occurred_at)
                VALUES (:eventId, :aggregateId, :aggregateType, :eventType, CAST(:payload AS jsonb), :occurredAt)
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", event.eventId())
                        .addValue("aggregateId", event.aggregateId())
                        .addValue("aggregateType", event.aggregateType())
                        .addValue("eventType", event.eventType())
                        .addValue("payload", event.payload())
                        .addValue("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC)));
    }

    @Override
    public List<OutboxEvent> fetchUnpublished(int limit) {
        return jdbc.query("""
                SELECT event_id, aggregate_id, aggregate_type, event_type, payload, occurred_at
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY occurred_at, event_id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """,
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()));
    }

    @Override
    public void markPublished(UUID eventId) {
        jdbc.update("UPDATE outbox SET published_at = now() WHERE event_id = :eventId",
                new MapSqlParameterSource().addValue("eventId", eventId));
    }
}
