package com.platform.shared.dispatcher;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link ProcessedEventStore}. The claim is a single {@code INSERT ... ON CONFLICT DO
 * NOTHING}; the affected-row count tells the caller whether this consumer is seeing the event for
 * the first time. The {@code processed_events} primary key {@code (consumer, event_id)} is the
 * unique constraint that makes replays no-ops.
 */
@Repository
public class JdbcProcessedEventStore implements ProcessedEventStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProcessedEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(String consumer, UUID eventId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_events (consumer, event_id)
                VALUES (:consumer, :eventId)
                ON CONFLICT (consumer, event_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("consumer", consumer)
                        .addValue("eventId", eventId));
        return inserted == 1;
    }
}
