package com.platform.ai.domain;

import java.util.UUID;

/** Result of the 4-signal verifier for one candidate answer (§10.3–10.4). */
public record VerificationResult(
        UUID id,
        UUID questionId,
        double groundednessScore,
        double structuralScore,
        double consistencyScore,
        double mathScore,
        double aggregateScore) {
}
