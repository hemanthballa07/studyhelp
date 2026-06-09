plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.platform"
version = "0.0.1-SNAPSHOT"

// Override Spring Boot's managed Testcontainers (1.20.4) so docker-java is new enough to talk to a
// current Docker Desktop daemon (Engine 29 / API 1.54), which 1.20.4's docker-java rejects with 400.
extra["testcontainers.version"] = "1.21.4"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-authorization-server")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// `test` runs everything except the ArchUnit conformance suite...
tasks.named<Test>("test") {
    useJUnitPlatform()
    filter { excludeTestsMatching("com.platform.arch.*") }
}

// ...which gets its own task so CI can run `./gradlew test archTest`.
val archTest = tasks.register<Test>("archTest") {
    description = "Runs ArchUnit architecture-conformance tests (package-by-context boundaries)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.platform.arch.*") }
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(archTest)
}
