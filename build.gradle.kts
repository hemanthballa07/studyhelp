plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.platform"
version = "0.0.1-SNAPSHOT"

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
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
