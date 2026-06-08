package com.platform.qc.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQcReviewRepository implements QcReviewRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcQcReviewRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(UUID id, UUID answerId, UUID questionId, UUID expertId, RubricScore score) {
        jdbc.update("""
                INSERT INTO qc_reviews
                    (id, answer_id, question_id, expert_id, total_score, status,
                     dimensions_json, violations_json, suggestions_json)
                VALUES
                    (:id, :answerId, :questionId, :expertId, :totalScore, :status,
                     CAST(:dimensionsJson AS jsonb), CAST(:violationsJson AS jsonb), CAST(:suggestionsJson AS jsonb))
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("answerId", answerId)
                        .addValue("questionId", questionId)
                        .addValue("expertId", expertId)
                        .addValue("totalScore", score.totalScore())
                        .addValue("status", score.status().name())
                        .addValue("dimensionsJson", toJson(score.dimensions()))
                        .addValue("violationsJson", toJson(score.violations()))
                        .addValue("suggestionsJson", toJson(score.suggestions())));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize qc_reviews column", ex);
        }
    }
}
