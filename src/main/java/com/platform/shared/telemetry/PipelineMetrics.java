package com.platform.shared.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Central registration point for AI pipeline meters (§11.2). All meters are pre-registered
 * eagerly so they appear in /actuator/prometheus before the first observation.
 * Only aggregate tags — no question/expert IDs (cardinality rule).
 */
@Component
public class PipelineMetrics {

    private final MeterRegistry registry;
    private final Timer pipelineTimer;
    private final Map<String, Counter> decisionCounters;
    private final Counter retrievalHit;
    private final Counter retrievalMiss;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;

        pipelineTimer = Timer.builder("ai.pipeline.latency")
                .description("End-to-end AI pipeline latency (retrieve→generate→verify→decide)")
                .publishPercentileHistogram()
                .register(registry);

        decisionCounters = new HashMap<>();
        for (String outcome : List.of("PRODUCED", "FLAGGED", "ABSTAINED")) {
            decisionCounters.put(outcome,
                    Counter.builder("ai.decision.count")
                            .description("AI answer decisions by outcome")
                            .tag("outcome", outcome)
                            .register(registry));
        }

        retrievalHit = Counter.builder("ai.retrieval.hit")
                .description("Corpus retrieval hit/miss counts")
                .tag("result", "hit")
                .register(registry);
        retrievalMiss = Counter.builder("ai.retrieval.hit")
                .description("Corpus retrieval hit/miss counts")
                .tag("result", "miss")
                .register(registry);
    }

    public Timer.Sample startPipeline() {
        return Timer.start(registry);
    }

    public void stopPipeline(Timer.Sample sample) {
        sample.stop(pipelineTimer);
    }

    public void recordDecision(String outcome) {
        decisionCounters.getOrDefault(outcome, decisionCounters.get("PRODUCED")).increment();
    }

    public void recordRetrievalHit(boolean hit) {
        (hit ? retrievalHit : retrievalMiss).increment();
    }

    public void recordModelApiError(String stage) {
        Counter.builder("ai.model.error")
                .description("Model API and embedding call errors by stage")
                .tag("stage", stage)
                .register(registry)
                .increment();
    }
}
