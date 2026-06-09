package com.platform.search.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.search.domain.SearchRepository;
import com.platform.search.event.ContentIndexed;
import com.platform.shared.embedding.EmbeddingPort;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private final SearchRepository repo;
    private final OutboxStore outbox;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final EmbeddingPort embeddingPort;

    public SearchService(SearchRepository repo, OutboxStore outbox, Clock clock,
            ObjectMapper objectMapper, EmbeddingPort embeddingPort) {
        this.repo = repo;
        this.outbox = outbox;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.embeddingPort = embeddingPort;
    }

    // Embedding is computed before the transaction opens so a transient model failure does not
    // roll back the FTS upsert, and the connection is not held during remote inference.
    public void indexQuestion(UUID questionId, String subject, String title, String body) {
        float[] embedding = embeddingPort.embed(title + " " + body);
        persistIndex(questionId, subject, title, body, embedding);
    }

    @Transactional
    public void persistIndex(UUID questionId, String subject, String title, String body, float[] embedding) {
        repo.upsert(questionId, subject, title, body);
        repo.upsertChunk(questionId, title + " " + body, embedding);
        String payload = toJson(new ContentIndexed(questionId));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), questionId, "Question", ContentIndexed.TYPE, payload, clock.instant()));
    }

    @Transactional
    public void touchIndexed(UUID questionId) {
        repo.touchIndexed(questionId);
    }

    @Transactional(readOnly = true)
    public List<UUID> findDuplicates(UUID questionId, String subject, String title, String body) {
        return repo.findDuplicates(questionId, subject, title, body, 0.1f);
    }

    @Transactional(readOnly = true)
    public List<UUID> search(String query, int limit) {
        return repo.search(query, limit);
    }

    // Embedding computed before the transaction so the connection is not held during model inference.
    public List<UUID> hybridSearch(String query, int limit) {
        float[] queryEmbedding = embeddingPort.embed(query);
        return queryHybrid(query, queryEmbedding, limit);
    }

    @Transactional(readOnly = true)
    public List<UUID> queryHybrid(String query, float[] queryEmbedding, int limit) {
        List<UUID> fts = repo.search(query, limit);
        List<UUID> vector = repo.findSimilar(queryEmbedding, limit);

        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < fts.size(); i++) {
            scores.merge(fts.get(i), 1.0 / (60 + i + 1), Double::sum);
        }
        for (int i = 0; i < vector.size(); i++) {
            scores.merge(vector.get(i), 1.0 / (60 + i + 1), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize search event payload", ex);
        }
    }
}
