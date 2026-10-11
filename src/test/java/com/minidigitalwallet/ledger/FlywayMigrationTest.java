package com.minidigitalwallet.ledger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Flyway V1..V4 migrations apply cleanly on real PostgreSQL 16
 * (H2 never executes this SQL in unit tests, so syntax drift would go unnoticed).
 */
@SpringBootTest
@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Let Flyway own the schema; skip Hibernate validation because Phase 3/4
        // entities (outbox, idempotency) have no migrations yet by design.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Flyway applies V1..V5 on PostgreSQL without errors")
    void flyway_MigratesCleanly() {
        var applied = flyway.info().applied();
        assertThat(applied).hasSize(5);
        assertThat(applied[applied.length - 1].getScript())
            .contains("V5__idempotency_schema.sql");
    }
}
