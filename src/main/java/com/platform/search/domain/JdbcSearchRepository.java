package com.platform.search.domain;

import java.util.Arrays;
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
        // ts_rank without @@ intentionally allows partial-match scoring so near-duplicates are
        // caught even when candidate and incoming question share most but not all lexemes.
        // Trade-off: threshold 0.1 is calibrated for small corpora and must be revisited at scale.
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

    @Override
    public void upsertChunk(UUID questionId, String text, float[] embedding) {
        jdbc.update("""
                INSERT INTO corpus_chunk (id, question_id, chunk_text, embedding)
                VALUES (:id, :questionId, :text, CAST(:embedding AS vector))
                ON CONFLICT (question_id) DO UPDATE
                    SET chunk_text  = EXCLUDED.chunk_text,
                        embedding   = EXCLUDED.embedding,
                        indexed_at  = now()
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("questionId", questionId)
                        .addValue("text", text)
                        .addValue("embedding", Arrays.toString(embedding)));
    }

    @Override
    public List<UUID> findSimilar(float[] queryEmbedding, int limit) {
        return jdbc.query("""
                SELECT question_id
                FROM corpus_chunk
                ORDER BY embedding <=> CAST(:qv AS vector)
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("qv", Arrays.toString(queryEmbedding))
                        .addValue("limit", limit),
                (rs, rowNum) -> rs.getObject("question_id", UUID.class));
    }
}
