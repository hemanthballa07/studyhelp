package com.platform.shared.embedding;

import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class DeterministicStubEmbeddingAdapter implements EmbeddingPort {

    private final int dimension;

    public DeterministicStubEmbeddingAdapter(
            @Value("${platform.embedding.dimension:384}") int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimension];
        Random rng = new Random(text.hashCode());
        for (int i = 0; i < dimension; i++) {
            v[i] = rng.nextFloat() * 2 - 1;
        }
        return v;
    }
}
