package com.platform.qc.domain;

import java.util.List;
import java.util.Map;

/**
 * The output of one rubric evaluation: per-dimension scores, detected violations, suggestions,
 * and the band (master-design §7). Immutable; all collections are unmodifiable.
 */
public record RubricScore(
        int totalScore,
        Map<String, Integer> dimensions,
        List<String> violations,
        List<String> suggestions,
        QcStatus status) {
}
