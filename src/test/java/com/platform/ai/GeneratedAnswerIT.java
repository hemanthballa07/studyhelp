package com.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.CorpusIngestionService;
import com.platform.ai.app.GenerationService;
import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.support.PostgresContainerSupport;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that:
 * 1. Generated steps carry chunk citations (acceptance criterion 1).
 * 2. Uncited-claim answers are detectable (acceptance criterion 2).
 * 3. Candidate answer is persisted and retrievable.
 */
@SpringBootTest
class GeneratedAnswerIT extends PostgresContainerSupport {

    @Autowired CorpusIngestionService ingestion;
    @Autowired GenerationService generation;
    @Autowired GenerationRepository generationRepo;

    @BeforeEach
    void seedCorpus() {
        ingestion.seed();
    }

    @Test
    void generatedStepsCarryCitations() {
        UUID questionId = UUID.randomUUID();

        CandidateAnswer answer = generation.generate(questionId, "Newton second law force mass");

        assertThat(answer.steps()).isNotEmpty();
        assertThat(answer.steps()).allSatisfy(step ->
                assertThat(step.citationChunkIds()).isNotEmpty());
        assertThat(answer.isFullyCited()).isTrue();
    }

    @Test
    void uncitedClaimAnswerIsDetectable() {
        CandidateAnswer uncited = new CandidateAnswer(
                List.of(new AnswerStep("ungrounded claim", List.of())));

        assertThat(uncited.hasUncitedClaims()).isTrue();
        assertThat(uncited.isFullyCited()).isFalse();
        assertThat(uncited.citationCoverage()).isEqualTo(0.0);
    }

    @Test
    void candidateAnswerIsPersistedAndRetrievable() {
        UUID questionId = UUID.randomUUID();

        generation.generate(questionId, "kinetic energy work theorem");

        CandidateAnswer persisted = generationRepo.findByQuestionId(questionId).orElseThrow();
        assertThat(persisted.steps()).isNotEmpty();
        assertThat(persisted.steps()).allSatisfy(step -> assertThat(step.text()).isNotBlank());
    }

    @Test
    void citationChunkIdsReferenceActualCorpusChunks() {
        UUID questionId = UUID.randomUUID();

        CandidateAnswer answer = generation.generate(questionId, "quadratic formula discriminant");

        Set<UUID> corpusIds = CorpusIngestionService.SEED_CHUNKS.stream()
                .map(CorpusIngestionService.SeedChunk::id)
                .collect(Collectors.toSet());

        answer.steps().forEach(step ->
                step.citationChunkIds().forEach(id ->
                        assertThat(corpusIds).contains(id)));
    }
}
