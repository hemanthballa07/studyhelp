package com.platform.ai.domain;

import java.util.List;
import java.util.UUID;

public interface CorpusRepository {
    void upsertChunk(UUID id, String source, String license, String chunkText, float[] embedding);
    List<AiCorpusChunk> findByFts(String query, int limit);
    List<AiCorpusChunk> findByVector(float[] embedding, int limit);
    void recordAnswerRequest(UUID questionId);
    boolean hasAnswerRequest(UUID questionId);
    long corpusSize();
}
