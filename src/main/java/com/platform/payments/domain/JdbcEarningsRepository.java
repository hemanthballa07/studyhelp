package com.platform.payments.domain;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEarningsRepository implements EarningsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEarningsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int accrue(UUID sourceEventId, UUID questionId, UUID expertId, int amountCents) {
        return jdbc.update("""
                INSERT INTO earnings (source_event_id, question_id, expert_id, amount_cents)
                VALUES (:sourceEventId, :questionId, :expertId, :amountCents)
                ON CONFLICT (source_event_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("sourceEventId", sourceEventId)
                        .addValue("questionId", questionId)
                        .addValue("expertId", expertId)
                        .addValue("amountCents", amountCents));
    }
}
