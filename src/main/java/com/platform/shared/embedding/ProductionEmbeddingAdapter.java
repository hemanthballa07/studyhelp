package com.platform.shared.embedding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ProductionEmbeddingAdapter implements EmbeddingPort {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ProductionEmbeddingAdapter.class);

    private final int dimension;

    public ProductionEmbeddingAdapter(
            @org.springframework.beans.factory.annotation.Value("${platform.embedding.dimension:384}") int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        // Returns a zero vector until spring-ai is wired (Slice 12). Logged at WARN so local
        // bootRun and staging remain functional without a live model.
        log.warn("Embedding model not provisioned; returning zero vector. Add spring-ai and configure spring.ai.*");
        return new float[dimension];
    }
}
