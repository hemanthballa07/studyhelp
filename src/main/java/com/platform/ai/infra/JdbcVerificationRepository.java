package com.platform.ai.infra;

import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVerificationRepository implements VerificationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVerificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int save(VerificationResult result) {
        return jdbc.update("""
                INSERT INTO verifications
                    (id, question_id, groundedness_score, structural_score,
                     consistency_score, math_score, aggregate_score)
                VALUES
                    (:id, :questionId, :groundedness, :structural, :consistency, :math, :aggregate)
                ON CONFLICT (question_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", result.id())
                        .addValue("questionId", result.questionId())
                        .addValue("groundedness", result.groundednessScore())
                        .addValue("structural", result.structuralScore())
                        .addValue("consistency", result.consistencyScore())
                        .addValue("math", result.mathScore())
                        .addValue("aggregate", result.aggregateScore()));
    }

    @Override
    public Optional<VerificationResult> findByQuestionId(UUID questionId) {
        return jdbc.query("""
                SELECT id, question_id, groundedness_score, structural_score,
                       consistency_score, math_score, aggregate_score
                FROM verifications
                WHERE question_id = :questionId
                """,
                new MapSqlParameterSource("questionId", questionId),
                (rs, rowNum) -> new VerificationResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("question_id", UUID.class),
                        rs.getDouble("groundedness_score"),
                        rs.getDouble("structural_score"),
                        rs.getDouble("consistency_score"),
                        rs.getDouble("math_score"),
                        rs.getDouble("aggregate_score")))
                .stream().findFirst();
    }
}
