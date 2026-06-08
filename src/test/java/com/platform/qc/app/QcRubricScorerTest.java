package com.platform.qc.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.qc.domain.QcStatus;
import com.platform.qc.domain.RubricScore;
import org.junit.jupiter.api.Test;

class QcRubricScorerTest {

    private final QcRubricScorer scorer = new QcRubricScorer();

    @Test
    void wellStructuredPhysicsBodyPassesWithHighScore() {
        String body = """
                1. Identify the given values: mass = 2 kg, acceleration = 5 m/s.

                2. Apply Newton's second law $F = ma$ to find force.

                $F = 2 \\times 5 = 10$ N

                Therefore, the answer is F = 10 N, which has units of Newtons.
                """;
        RubricScore score = scorer.score(body, "PHYSICS");
        assertThat(score.status()).isEqualTo(QcStatus.PASS);
        assertThat(score.totalScore()).isGreaterThanOrEqualTo(75);
    }

    @Test
    void shortBodyFailsWithLowScore() {
        String body = "42";
        RubricScore score = scorer.score(body, "MATH");
        assertThat(score.status()).isEqualTo(QcStatus.FAIL);
        assertThat(score.totalScore()).isLessThan(60);
    }

    @Test
    void singleStepNoLatexNoFinalAnswerScoresRevision() {
        // 1 step (10), no LaTeX (0), no final-answer marker (0), MATH units (10),
        // explanation depth ≥2 (10), readability (10), compliance (10), similarity (15) = 65
        String body = """
                1. We start by identifying the key values given in the problem statement.
                The problem asks us to find the product of two positive integers.
                We can use basic multiplication to find the result because that is straightforward.
                The calculation should be done carefully to avoid arithmetic mistakes.
                """;
        RubricScore score = scorer.score(body, "MATH");
        assertThat(score.status()).isEqualTo(QcStatus.REVISION_REQUESTED);
        assertThat(score.totalScore()).isBetween(60, 74);
    }

    @Test
    void physicsBodyWithUnitsTokenScoresFullUnitsPoints() {
        String body = """
                1. Given: mass = 5 kg, acceleration = 3 m/s

                2. Apply $F = ma$

                Therefore F = 15 N
                """;
        RubricScore score = scorer.score(body, "PHYSICS");
        assertThat(score.dimensions()).containsKey("UNITS");
        assertThat(score.dimensions().get("UNITS")).isEqualTo(10);
    }

    @Test
    void latexPresentScoresEquationFormattingPoints() {
        String body = """
                1. The energy formula is $E = mc^2$ where m is mass in kg.

                2. Substituting: $E = 2 \\times (3 \\times 10^8)^2$

                Therefore $E = 1.8 \\times 10^{17}$ J
                """;
        RubricScore score = scorer.score(body, "PHYSICS");
        assertThat(score.dimensions()).containsKey("EQUATION_FORMATTING");
        assertThat(score.dimensions().get("EQUATION_FORMATTING")).isEqualTo(10);
    }

    @Test
    void physicsBodyMissingUnitTokensScoresZeroUnits() {
        String body = """
                1. Apply the formula $F = ma$ where m=5, a=3.

                2. Calculate: $F = 15$

                Therefore the answer is 15.
                """;
        RubricScore score = scorer.score(body, "PHYSICS");
        assertThat(score.dimensions().get("UNITS")).isEqualTo(0);
    }

    @Test
    void policyViolationKeywordScoresZeroComplianceAndLogsViolation() {
        String body = """
                1. Here is what the answer key says about this problem.

                2. The solution follows the standard approach.

                Therefore the answer is 42.
                """;
        RubricScore score = scorer.score(body, "MATH");
        assertThat(score.dimensions().get("POLICY_COMPLIANCE")).isEqualTo(0);
        assertThat(score.violations()).isNotEmpty();
    }
}
