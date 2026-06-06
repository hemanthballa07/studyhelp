package com.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * Walking-skeleton acceptance test: the full application context boots against a real Postgres and
 * {@code /actuator/health} reports UP, exercising the datasource health indicator end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT extends PostgresContainerSupport {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUp() {
        String body = restTemplate.getForObject("/actuator/health", String.class);
        assertThat(body).contains("\"status\":\"UP\"");
    }
}
