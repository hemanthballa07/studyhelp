package com.platform.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests needing a real Postgres. Singleton-container pattern: started once per
 * JVM and reused by every IT class (Ryuk reaps it at JVM exit). Deliberately not annotated with
 * {@code @Testcontainers}; its per-class stop would kill a container that other cached Spring
 * contexts still point at, which breaks classes that share a context (see ConnectException on CI).
 */
public abstract class PostgresContainerSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
