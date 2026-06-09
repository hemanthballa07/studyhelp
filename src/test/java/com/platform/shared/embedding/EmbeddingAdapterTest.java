package com.platform.shared.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingAdapterTest {

    private final DeterministicStubEmbeddingAdapter adapter = new DeterministicStubEmbeddingAdapter(384);

    @Test
    void sameTextProducesSameVector() {
        assertThat(adapter.embed("hello world")).isEqualTo(adapter.embed("hello world"));
    }

    @Test
    void differentTextProducesDifferentVector() {
        assertThat(adapter.embed("hello")).isNotEqualTo(adapter.embed("world"));
    }

    @Test
    void vectorHasCorrectDimension() {
        assertThat(adapter.embed("test")).hasSize(384);
    }
}
