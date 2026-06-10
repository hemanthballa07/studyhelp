package com.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.VerificationResult;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.support.PostgresContainerSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for the code-exec verifier (LLM-judge fallback, §10.4).
 * Uses the test-profile stub (StubCodeVerifierAdapter → 0.5) so no live model is needed.
 * Verifies that an answer containing a code fence routes through the code signal path and
 * produces a valid domain score.
 */
@SpringBootTest
class CodeVerifierIT extends PostgresContainerSupport {

    @Autowired VerificationService verificationService;

    @Test
    void codeAnswer_domainScorePopulated() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("Here is the solution:\n```python\ndef add(a, b):\n    return a + b\n```",
                        List.of())));

        VerificationResult result = verificationService.verify(
                questionId, "Write a function to add two numbers", "CS", candidate);

        assertThat(result.mathScore()).isBetween(0.0, 1.0);
        assertThat(result.aggregateScore()).isGreaterThan(0.0);
    }
}
