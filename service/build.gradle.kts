// Spring Boot service: runs simulations on request and stores their results in Postgres.
plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

// Newer than Boot's managed version: 1.21.4 is the first that talks to current Docker engines.
ext["testcontainers.version"] = "1.21.4"

dependencies {
    implementation(project(":sim"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // The integration tests start Postgres in Docker. Point Testcontainers at Colima when that is what is installed.
    val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://$colimaSocket")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}
