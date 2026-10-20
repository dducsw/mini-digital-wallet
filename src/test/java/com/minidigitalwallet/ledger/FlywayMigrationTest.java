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
 * Proves the Flyway migrations under db/migration apply cleanly on real
 * PostgreSQL 16 (H2 never executes this SQL in unit tests, so syntax drift
 * would go unnoticed).
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
        // Let Flyway own the schema; skip Hibernate validation for this test.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Every migration on the classpath is applied on PostgreSQL without errors")
    void flyway_MigratesCleanly() {
        var info = flyway.info();

        // Nothing left to apply => the whole chain ran end to end.
        assertThat(info.pending())
            .as("all migrations must already be applied by the Spring context")
            .isEmpty();

        var applied = info.applied();
        assertThat(applied).isNotEmpty();
        assertThat(applied).allMatch(m -> !m.getState().isFailed());

        // Guard against silently dropping the newest script during a refactor.
        assertThat(applied[applied.length - 1].getScript())
            .contains("V11__user_role.sql");
    }
}
