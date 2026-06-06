# Study-Help Platform

A Java 21 / Spring Boot study-help platform: expert-style question routing, atomic claim
handling (Postgres `FOR UPDATE SKIP LOCKED`), rubric-based answer QC, hybrid search, and a
grounded AI answer-verification layer with end-to-end observability.

> Personal portfolio project. Payments are a simulated ledger; the corpus uses open-licensed
> data only.

## Stack
Java 21 · Spring Boot · Spring Cloud Gateway · Spring Authorization Server · Postgres + pgvector ·
Redis · S3/MinIO · transactional outbox → Kafka · Resilience4j · OpenTelemetry + Prometheus +
Grafana · Gradle · Testcontainers · ArchUnit · GitHub Actions · ECS/Fargate.

## Architecture
Modular monolith, package-by-context, event-driven via transactional outbox. Each context owns
one canonical truth; cross-context effects flow through domain events. See `docs/master-design.md`.

## Build order
See `BACKLOG.md` (20 vertical slices). Build one per iteration; a slice is done only when its
tests (incl. adversarial/arch tests) are green.

## Local dev
1. `cp .env.example .env` and fill in real values (`.env` is gitignored).
2. `pip install pre-commit && pre-commit install` (enables the gitleaks pre-commit hook).
3. `docker compose up -d` (Postgres + Redis), then `./gradlew bootRun`. Health: `/actuator/health`.
4. Run the gate: `./gradlew test archTest` (integration tests use Testcontainers, so Docker must be running).

Requires JDK 21; the Gradle toolchain will provision it automatically if it isn't installed.
