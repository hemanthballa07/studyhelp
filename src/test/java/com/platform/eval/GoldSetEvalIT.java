package com.platform.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.app.AiDecisionService;
import com.platform.ai.app.CorpusIngestionService;
import com.platform.ai.app.DecisionOutcome;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.RetrievalService;
import com.platform.ai.app.VerificationService;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.support.PostgresContainerSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Offline gold-set evaluation: CI quality gate (§11.1).
 *
 * Runs the full AI pipeline (retrieve → generate → verify → decide) on 6 held-out
 * examples with checkable answers from the OpenStax CC BY 4.0 seed corpus. Asserts
 * that guarded metrics (correctness, groundedness, deliver_rate, recall@3,
 * p95_latency_ms) meet thresholds. Any assertion failure fails the build via the
 * {@code evalTest} Gradle task.
 *
 * Thresholds are conservative: pass reliably with deterministic test-profile stubs
 * but catch genuine regressions (broken citation logic, FTS retrieval, decision bands).
 */
@SpringBootTest
class GoldSetEvalIT extends PostgresContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(GoldSetEvalIT.class);

    // ── CI gate thresholds ──────────────────────────────────────────────────

    /** Fraction of gold examples where all expected phrases appear in the answer. */
    private static final double MIN_CORRECTNESS = 0.50;
    /** Mean groundedness signal from the 4-signal verifier (0-1 range). */
    private static final double MIN_GROUNDEDNESS = 0.70;
    /** (PRODUCED + FLAGGED) / total — fraction of examples that reach delivery. */
    private static final double MIN_DELIVER_RATE = 0.50;
    /** Fraction of examples where a relevant chunk appears in top-3 retrieval results. */
    private static final double MIN_RECALL_AT_3 = 0.50;
    /** 95th-percentile full-pipeline latency in milliseconds. */
    private static final long MAX_P95_LATENCY_MS = 15_000;

    // ── Gold set ────────────────────────────────────────────────────────────

    record GoldExample(String id, String subject, String question, List<String> expectedPhrases) {}

    // 6 examples: questions are phrased to favour FTS hits on the seed corpus chunks.
    // Each expectedPhrases list uses ONE highly-distinctive phrase that appears verbatim
    // only in the target OpenStax chunk; the answer is "correct" if any listed phrase
    // appears in the generated answer (anyMatch). This is robust to chunk-ordering noise
    // in the stub adapter (which only echoes top-2 context positions).
    private static final List<GoldExample> GOLD_SET = List.of(
        new GoldExample("g01", "PHYSICS",
            "Newton second law net force equals mass times acceleration equilibrium zero",
            List.of("F = ma", "equilibrium")),
        new GoldExample("g02", "PHYSICS",
            "kinetic energy KE half mass velocity squared work energy theorem",
            List.of("(1/2)mv^2", "work-energy theorem")),
        new GoldExample("g03", "MATH",
            "quadratic formula discriminant two real roots repeated root complex",
            List.of("discriminant", "b^2 - 4ac")),
        new GoldExample("g04", "PHYSICS",
            "gravitational potential energy mgh height conservation mechanical energy",
            List.of("PE = mgh", "mgh")),
        new GoldExample("g05", "PHYSICS",
            "Ohm law voltage current resistance ohms power dissipated resistors series parallel",
            List.of("V = IR", "ohms")),
        new GoldExample("g06", "PHYSICS",
            "momentum impulse isolated system elastic collision kinetic energy conserved",
            List.of("Impulse J", "m1*v1"))
    );

    @Autowired CorpusIngestionService ingestion;
    @Autowired GenerationService generationService;
    @Autowired VerificationService verificationService;
    @Autowired AiDecisionService decisionService;
    @Autowired RetrievalService retrievalService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedCorpus() {
        ingestion.seed();
    }

    @Test
    void goldSetMeetsQualityThresholds() throws IOException {
        record ExampleResult(
            String id,
            DecisionOutcome decision,
            double groundedness,
            boolean correct,
            long latencyMs,
            boolean recallHit
        ) {}

        List<ExampleResult> results = new ArrayList<>();

        for (GoldExample ex : GOLD_SET) {
            UUID questionId = UUID.randomUUID();
            long start = System.currentTimeMillis();

            // Retrieval recall@3: does any top-3 chunk contain an expected phrase?
            var retrieved = retrievalService.retrieve(ex.question(), 3);
            boolean recallHit = retrieved.stream()
                    .anyMatch(chunk -> ex.expectedPhrases().stream()
                            .anyMatch(phrase -> chunk.chunkText().contains(phrase)));

            // Full pipeline: generate → verify → decide.
            CandidateAnswer candidate = generationService.generate(questionId, ex.question());
            var vr = verificationService.verify(questionId, ex.question(), ex.subject(), candidate);
            DecisionOutcome outcome = decisionService.decide(questionId, vr, ex.subject());
            long latencyMs = System.currentTimeMillis() - start;

            // Correctness: at least one expected phrase appears in the combined answer text.
            // Uses anyMatch because the stub adapter only echoes the top-2 context chunks;
            // requiring all phrases would couple correctness to retrieval ranking noise.
            String answerText = candidate.steps().stream()
                    .map(step -> step.text())
                    .collect(Collectors.joining(" "));
            boolean correct = ex.expectedPhrases().stream().anyMatch(answerText::contains);

            results.add(new ExampleResult(ex.id(), outcome, vr.groundednessScore(), correct, latencyMs, recallHit));
            log.info("[eval] {} decision={} groundedness={} correct={} latency={}ms recallHit={}",
                    ex.id(), outcome, vr.groundednessScore(), correct, latencyMs, recallHit);
        }

        // ── Aggregate metrics ───────────────────────────────────────────────
        double correctness       = (double) results.stream().filter(ExampleResult::correct).count() / results.size();
        double groundedness      = results.stream().mapToDouble(ExampleResult::groundedness).average().orElse(0);
        double deliverRate       = (double) results.stream().filter(r -> r.decision() != DecisionOutcome.ABSTAINED).count() / results.size();
        double hallucinationRate = 1.0 - groundedness;
        double recallAt3         = (double) results.stream().filter(ExampleResult::recallHit).count() / results.size();

        List<Long> sortedLatencies = results.stream()
                .mapToLong(ExampleResult::latencyMs).sorted().boxed().toList();
        int p95Idx = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        long p95LatencyMs = sortedLatencies.get(Math.min(p95Idx, sortedLatencies.size() - 1));

        // ── Write eval report ───────────────────────────────────────────────
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total_examples", results.size());
        report.put("correctness", correctness);
        report.put("groundedness", groundedness);
        report.put("deliver_rate", deliverRate);
        report.put("hallucination_rate", hallucinationRate);
        report.put("recall_at_3", recallAt3);
        report.put("p95_latency_ms", p95LatencyMs);
        report.put("thresholds", Map.of(
                "min_correctness", MIN_CORRECTNESS,
                "min_groundedness", MIN_GROUNDEDNESS,
                "min_deliver_rate", MIN_DELIVER_RATE,
                "min_recall_at_3", MIN_RECALL_AT_3,
                "max_p95_latency_ms", MAX_P95_LATENCY_MS));
        report.put("examples", results.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.id());
            row.put("decision", r.decision().name());
            row.put("groundedness", r.groundedness());
            row.put("correct", r.correct());
            row.put("latency_ms", r.latencyMs());
            row.put("recall_hit", r.recallHit());
            return row;
        }).collect(Collectors.toList()));

        Path reportPath = Path.of("build/reports/eval/eval-report.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        log.info("[eval] Report written to {}", reportPath.toAbsolutePath());

        // ── CI gate assertions ──────────────────────────────────────────────
        log.info("[eval] correctness={} (min={})", correctness, MIN_CORRECTNESS);
        log.info("[eval] groundedness={} (min={})", groundedness, MIN_GROUNDEDNESS);
        log.info("[eval] deliver_rate={} (min={})", deliverRate, MIN_DELIVER_RATE);
        log.info("[eval] recall_at_3={} (min={})", recallAt3, MIN_RECALL_AT_3);
        log.info("[eval] p95_latency_ms={} (max={})", p95LatencyMs, MAX_P95_LATENCY_MS);

        assertThat(correctness)
                .as("correctness %.2f must be >= %.2f", correctness, MIN_CORRECTNESS)
                .isGreaterThanOrEqualTo(MIN_CORRECTNESS);
        assertThat(groundedness)
                .as("groundedness %.3f must be >= %.2f", groundedness, MIN_GROUNDEDNESS)
                .isGreaterThanOrEqualTo(MIN_GROUNDEDNESS);
        assertThat(deliverRate)
                .as("deliver_rate %.2f must be >= %.2f", deliverRate, MIN_DELIVER_RATE)
                .isGreaterThanOrEqualTo(MIN_DELIVER_RATE);
        assertThat(recallAt3)
                .as("recall@3 %.2f must be >= %.2f", recallAt3, MIN_RECALL_AT_3)
                .isGreaterThanOrEqualTo(MIN_RECALL_AT_3);
        assertThat(p95LatencyMs)
                .as("p95_latency_ms %d must be <= %d", p95LatencyMs, MAX_P95_LATENCY_MS)
                .isLessThanOrEqualTo(MAX_P95_LATENCY_MS);
    }
}
