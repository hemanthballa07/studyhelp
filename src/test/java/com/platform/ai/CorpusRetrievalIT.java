package com.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.CorpusIngestionService;
import com.platform.ai.app.RetrievalService;
import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.CorpusRepository;
import com.platform.support.PostgresContainerSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that:
 * 1. The seed corpus is ingested (open-licensed, license recorded).
 * 2. Top-k hybrid retrieval returns relevant chunks for probe questions.
 * 3. Answer request recording is idempotent.
 */
@SpringBootTest
class CorpusRetrievalIT extends PostgresContainerSupport {

    @Autowired CorpusIngestionService ingestion;
    @Autowired RetrievalService retrieval;
    @Autowired CorpusRepository repo;

    @BeforeEach
    void seedCorpus() {
        ingestion.seed();
    }

    @Test
    void seedProducesOpenLicensedChunks() {
        assertThat(repo.corpusSize()).isGreaterThanOrEqualTo(CorpusIngestionService.SEED_CHUNKS.size());
        assertThat(CorpusIngestionService.LICENSE).isEqualTo("CC BY 4.0");
        assertThat(CorpusIngestionService.SOURCE).isNotEmpty();
    }

    @Test
    void hybridRetrievalReturnsRelevantChunksForForceQuery() {
        List<AiCorpusChunk> results = retrieval.retrieve("Newton second law force mass acceleration", 3);

        assertThat(results).isNotEmpty();
        boolean newtonChunkFound = results.stream()
                .anyMatch(c -> c.chunkText().contains("Newton") || c.chunkText().contains("mass and acceleration"));
        assertThat(newtonChunkFound).isTrue();
    }

    @Test
    void hybridRetrievalReturnsRelevantChunksForQuadraticQuery() {
        List<AiCorpusChunk> results = retrieval.retrieve("quadratic formula discriminant roots", 3);

        assertThat(results).isNotEmpty();
        boolean quadraticFound = results.stream()
                .anyMatch(c -> c.chunkText().contains("quadratic") || c.chunkText().contains("discriminant"));
        assertThat(quadraticFound).isTrue();
    }

    @Test
    void answerRequestRecordingIsIdempotent() {
        UUID questionId = UUID.randomUUID();

        repo.recordAnswerRequest(questionId);
        repo.recordAnswerRequest(questionId);

        assertThat(repo.hasAnswerRequest(questionId)).isTrue();
    }

    @Test
    void seedIsIdempotent() {
        long beforeSize = repo.corpusSize();
        ingestion.seed();
        assertThat(repo.corpusSize()).isEqualTo(beforeSize);
    }
}
