package com.platform.ai.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGenerationRepository implements GenerationRepository {

    private static final TypeReference<List<AnswerStep>> STEPS_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcGenerationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UUID id, UUID questionId, CandidateAnswer answer) {
        String stepsJson = toJson(answer.steps());
        jdbc.update("""
                INSERT INTO generations (id, question_id, steps)
                VALUES (:id, :questionId, CAST(:steps AS jsonb))
                ON CONFLICT (question_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("questionId", questionId)
                        .addValue("steps", stepsJson));
    }

    @Override
    public Optional<CandidateAnswer> findByQuestionId(UUID questionId) {
        List<CandidateAnswer> rows = jdbc.query("""
                SELECT steps FROM generations WHERE question_id = :qId ORDER BY created_at DESC LIMIT 1
                """,
                new MapSqlParameterSource("qId", questionId),
                (rs, rowNum) -> {
                    List<AnswerStep> steps = fromJson(rs.getString("steps"));
                    return new CandidateAnswer(steps);
                });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize generation steps", ex);
        }
    }

    private List<AnswerStep> fromJson(String json) {
        try {
            return objectMapper.readValue(json, STEPS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize generation steps", ex);
        }
    }
}
