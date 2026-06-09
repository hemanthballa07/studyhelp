package com.platform.shared.embedding;

/**
 * Port for text embedding. Lives in {@code shared} so {@code search} can call it without a
 * compile-time dependency on the production model adapter (same pattern as {@code DedupPort}).
 */
public interface EmbeddingPort {
    float[] embed(String text);
}
