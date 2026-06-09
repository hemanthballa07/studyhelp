package com.platform.shared.embedding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ProductionEmbeddingAdapter implements EmbeddingPort {

    @Override
    public float[] embed(String text) {
        // Wire spring-ai dependency and a live model when the embedding server is provisioned.
        throw new UnsupportedOperationException(
                "Production embedding adapter not wired; add spring-ai dependency and configure spring.ai.*");
    }
}
