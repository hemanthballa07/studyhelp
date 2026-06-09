package com.platform.search.domain;

import java.util.List;
import java.util.UUID;

public interface SearchRepository {
    void upsert(UUID questionId, String subject, String title, String body);
    void touchIndexed(UUID questionId);
    List<UUID> findDuplicates(UUID questionId, String subject, String title, String body, float threshold);
    List<UUID> search(String query, int limit);
    void upsertChunk(UUID questionId, String text, float[] embedding);
    List<UUID> findSimilar(float[] queryEmbedding, int limit);
}
