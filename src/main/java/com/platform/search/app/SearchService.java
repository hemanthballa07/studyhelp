package com.platform.search.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.search.domain.SearchRepository;
import com.platform.search.event.ContentIndexed;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private final SearchRepository repo;
    private final OutboxStore outbox;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SearchService(SearchRepository repo, OutboxStore outbox, Clock clock, ObjectMapper objectMapper) {
        this.repo = repo;
        this.outbox = outbox;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void indexQuestion(UUID questionId, String subject, String title, String body) {
        repo.upsert(questionId, subject, title, body);
        String payload = toJson(new ContentIndexed(questionId));
        // ContentIndexed is pre-emptive for Slice 10 (pgvector embedding consumer); no handler
        // exists yet, so the relay marks these rows published without a consumer doing work.
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize search event payload", ex);
        }
    }
}
