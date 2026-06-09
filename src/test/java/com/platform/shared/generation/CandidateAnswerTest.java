package com.platform.shared.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateAnswerTest {

    private static final UUID CHUNK_A = UUID.randomUUID();

    @Test
    void fullyCited_whenAllStepsHaveCitations() {
        CandidateAnswer answer = new CandidateAnswer(List.of(
                new AnswerStep("step 1", List.of(CHUNK_A)),
                new AnswerStep("step 2", List.of(CHUNK_A))));

        assertThat(answer.isFullyCited()).isTrue();
        assertThat(answer.hasUncitedClaims()).isFalse();
        assertThat(answer.citationCoverage()).isEqualTo(1.0);
    }

    @Test
    void uncitedClaim_detectedWhenOneStepMissingCitation() {
        CandidateAnswer answer = new CandidateAnswer(List.of(
                new AnswerStep("cited step", List.of(CHUNK_A)),
                new AnswerStep("uncited step", List.of())));

        assertThat(answer.hasUncitedClaims()).isTrue();
        assertThat(answer.isFullyCited()).isFalse();
        assertThat(answer.citationCoverage()).isEqualTo(0.5);
    }

    @Test
    void emptySteps_notFullyCited() {
        CandidateAnswer answer = new CandidateAnswer(List.of());

        assertThat(answer.isFullyCited()).isFalse();
        assertThat(answer.citationCoverage()).isEqualTo(0.0);
    }

    @Test
    void allUncited_zeroPercentCoverage() {
        CandidateAnswer answer = new CandidateAnswer(List.of(
                new AnswerStep("no citation", List.of()),
                new AnswerStep("also no citation", List.of())));

        assertThat(answer.citationCoverage()).isEqualTo(0.0);
        assertThat(answer.hasUncitedClaims()).isTrue();
    }
}
