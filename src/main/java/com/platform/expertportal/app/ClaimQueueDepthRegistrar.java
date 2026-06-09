package com.platform.expertportal.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Registers a Prometheus gauge for the claimable question queue depth.
 * Kept in expertportal.app (not shared.telemetry) because it couples to the expertportal schema.
 * Polled on every Prometheus scrape — no caching needed at this scale.
 */
@Component
public class ClaimQueueDepthRegistrar {

    public ClaimQueueDepthRegistrar(MeterRegistry registry, NamedParameterJdbcTemplate jdbc) {
        Gauge.builder("claimable.queue.depth",
                        jdbc,
                        j -> {
                            Integer count = j.queryForObject(
                                    "SELECT COUNT(*) FROM claimable_questions",
                                    new MapSqlParameterSource(), Integer.class);
                            return count != null ? count : 0;
                        })
                .description("Number of questions currently waiting to be claimed")
                .register(registry);
    }
}
