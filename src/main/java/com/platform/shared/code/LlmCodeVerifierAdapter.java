package com.platform.shared.code;

import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.GenerationPort;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production note: this LLM judge is the time-constrained fallback per master-design §10.4.
 * For production, replace with a no-network, resource-capped sandbox executor
 * (ProcessBuilder + seccomp/gVisor) that runs the code against extracted test cases.
 */
@Component
@Profile("!test")
public class LlmCodeVerifierAdapter implements CodeVerifierPort {

    private static final Logger log = LoggerFactory.getLogger(LlmCodeVerifierAdapter.class);

    private final GenerationPort generationPort;

    public LlmCodeVerifierAdapter(GenerationPort generationPort) {
        this.generationPort = generationPort;
    }

    @Override
    public double verify(String questionText, String codeSnippet) {
        String prompt = "Code snippet:\n```\n" + codeSnippet + "\n```\n"
                + "Problem: " + questionText + "\n"
                + "Does this code correctly solve the problem? Answer only: CORRECT, INCORRECT, or UNCERTAIN.";
        try {
            CandidateAnswer judgment = generationPort.generate(prompt, List.of());
            String text = judgment.steps().stream()
                    .map(s -> s.text())
                    .collect(Collectors.joining(" "))
                    .toUpperCase();
            if (text.contains("INCORRECT")) return 0.0;
            if (text.contains("CORRECT")) return 1.0;
            return 0.5;
        } catch (Exception ex) {
            log.warn("LLM code verifier failed; returning neutral score. cause={}", ex.getMessage());
            return 0.5;
        }
    }
}
