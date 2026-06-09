package com.platform.ai.infra;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.CorpusRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCorpusRepository implements CorpusRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCorpusRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertChunk(UUID id, String source, String license, String chunkText, float[] embedding) {
        jdbc.update("""
                INSERT INTO ai_corpus_chunk (id, source, license, chunk_text, embedding)
                VALUES (:id, :source, :license, :chunkText, CAST(:embedding AS vector))
                ON CONFLICT (id) DO UPDATE
                    SET source     = EXCLUDED.source,
                        license    = EXCLUDED.license,
                        chunk_text = EXCLUDED.chunk_text,
                        embedding  = EXCLUDED.embedding,
                        indexed_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("source", source)
                        .addValue("license", license)
                        .addValue("chunkText", chunkText)
                        .addValue("embedding", Arrays.toString(embedding)));
    }

    @Override
    public List<AiCorpusChunk> findByFts(String query, int limit) {
        return jdbc.query("""
                SELECT id, source, license, chunk_text
                FROM ai_corpus_chunk,
                     plainto_tsquery('english', :query) q
                WHERE ts_content @@ q
                ORDER BY ts_rank(ts_content, q) DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("query", query)
                        .addValue("limit", limit),
                (rs, rowNum) -> new AiCorpusChunk(
                        rs.getObject("id", UUID.class),
                        rs.getString("source"),
                        rs.getString("license"),
                        rs.getString("chunk_text")));
    }

    @Override
    public List<AiCorpusChunk> findByVector(float[] embedding, int limit) {
        return jdbc.query("""
                SELECT id, source, license, chunk_text
                FROM ai_corpus_chunk
                ORDER BY embedding <=> CAST(:qv AS vector)
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("qv", Arrays.toString(embedding))
                        .addValue("limit", limit),
                (rs, rowNum) -> new AiCorpusChunk(
                        rs.getObject("id", UUID.class),
                        rs.getString("source"),
                        rs.getString("license"),
                        rs.getString("chunk_text")));
    }

    @Override
    public void recordAnswerRequest(UUID questionId) {
        jdbc.update("""
                INSERT INTO answer_requests (id, question_id)
                VALUES (:id, :questionId)
                ON CONFLICT (question_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("questionId", questionId));
    }

    @Override
    public boolean hasAnswerRequest(UUID questionId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM answer_requests WHERE question_id = :qId",
                new MapSqlParameterSource("qId", questionId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public long corpusSize() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM ai_corpus_chunk",
                new MapSqlParameterSource(),
                Long.class);
        return count == null ? 0L : count;
    }
}
