package com.platform.shared.generation;

import java.util.List;

/** A candidate answer produced by the generation model, pre-verifier. */
public record CandidateAnswer(List<AnswerStep> steps) {

    public CandidateAnswer {
        steps = List.copyOf(steps);
    }

    /** True if any step carries no citations (uncited claim). */
    public boolean hasUncitedClaims() {
        return steps.stream().anyMatch(s -> s.citationChunkIds().isEmpty());
    }

    /** True if every step is supported by at least one citation. */
    public boolean isFullyCited() {
        return !steps.isEmpty() && !hasUncitedClaims();
    }

    /** Fraction of steps that carry at least one citation (0.0–1.0). */
    public double citationCoverage() {
        if (steps.isEmpty()) return 0.0;
        long cited = steps.stream().filter(s -> !s.citationChunkIds().isEmpty()).count();
        return (double) cited / steps.size();
    }
}
