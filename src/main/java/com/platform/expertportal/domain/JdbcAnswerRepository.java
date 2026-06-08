package com.platform.expertportal.domain;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-backed {@link AnswerRepository}. */
@Repository
public class JdbcAnswerRepository implements AnswerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAnswerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID id, UUID questionId, UUID expertId, String body, boolean stale) {
        jdbc.update("""
                INSERT INTO answers (id, question_id, expert_id, body, stale)
                VALUES (:id, :questionId, :expertId, :body, :stale)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("questionId", questionId)
                        .addValue("expertId", expertId)
                        .addValue("body", body)
                        .addValue("stale", stale));
    }
}
