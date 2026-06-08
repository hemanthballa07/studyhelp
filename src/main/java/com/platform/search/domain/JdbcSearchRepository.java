package com.platform.search.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSearchRepository implements SearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(UUID questionId, String subject, String title, String body) {
        jdbc.update("""
                INSERT INTO corpus_index (question_id, subject, ts_content)
                VALUES (:qId, :subject,
                    setweight(to_tsvector('english', :title),   'A') ||
                    setweight(to_tsvector('english', :subject),  'B') ||
                    setweight(to_tsvector('english', :body),     'C'))
                ON CONFLICT (question_id) DO UPDATE
                    SET ts_content = EXCLUDED.ts_content,
                        subject    = EXCLUDED.subject,
                        indexed_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("qId", questionId)
                        .addValue("subject", subject)
                        .addValue("title", title)
                        .addValue("body", body));
    }

    @Override
    public void touchIndexed(UUID questionId) {
        jdbc.update(
                "UPDATE corpus_index SET indexed_at = now() WHERE question_id = :id",
                new MapSqlParameterSource("id", questionId));
    }

    @Override
    public List<UUID> findDuplicates(UUID questionId, String subject, String title, String body, float threshold) {
        String combined = title + " " + body;
        return jdbc.query("""
                SELECT question_id
                FROM corpus_index,
                     plainto_tsquery('english', :combined) q
                WHERE question_id != :qId
                  AND ts_rank(ts_content, q) > :threshold
                ORDER BY ts_rank(ts_content, q) DESC
                LIMIT 5
                """,
                new MapSqlParameterSource()
                        .addValue("combined", combined)
                        .addValue("qId", questionId)
                        .addValue("threshold", threshold),
                (rs, rowNum) -> rs.getObject("question_id", UUID.class));
    }

    @Override
    public List<UUID> search(String query, int limit) {
        return jdbc.query("""
                SELECT question_id
                FROM corpus_index,
                     plainto_tsquery('english', :query) q
                WHERE ts_content @@ q
                ORDER BY ts_rank(ts_content, q) DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("query", query)
                        .addValue("limit", limit),
                (rs, rowNum) -> rs.getObject("question_id", UUID.class));
    }
}
