package com.worksphere.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.worksphere.identity.infrastructure.IdentityRepository;
import com.worksphere.identity.infrastructure.PostgresLoginRateLimitStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgreSqlConnectionIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine");

  @Test
  void appliesIdentityMigrationToPostgreSql() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'password_credentials', 'user_sessions',
                                     'external_identities', 'oauth_authorization_transactions',
                                     'login_rate_limits')
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isEqualTo(6);
    }
  }

  @Test
  void persistsRateLimitsAndRenewsIdleExpiryWithoutExceedingAbsoluteExpiry() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    PostgresLoginRateLimitStore rateLimitStore = new PostgresLoginRateLimitStore(jdbcTemplate);
    Instant now = Instant.parse("2026-11-11T15:00:00Z");

    for (int attempt = 0; attempt < 5; attempt++) {
      rateLimitStore.recordFailure(
          "ACCOUNT", "a".repeat(64), 5, Duration.ofMinutes(10), Duration.ofMinutes(15), now);
    }
    assertThat(rateLimitStore.isBlocked("a".repeat(64), "b".repeat(64), now)).isTrue();

    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO users (
            id, email, normalized_email, first_name, last_name, display_name, status,
            email_verified_at, created_at, updated_at, version
        ) VALUES (?, 'test@example.com', ?, 'Test', 'User', 'Test User', 'ACTIVE', ?, ?, ?, 0)
        """,
        userId,
        userId + "@example.com",
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    jdbcTemplate.update(
        """
        INSERT INTO user_sessions (
            id, user_id, token_hash, created_at, idle_expires_at, absolute_expires_at, last_seen_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        sessionId,
        userId,
        "c".repeat(64),
        Timestamp.from(now),
        Timestamp.from(now.plus(Duration.ofMinutes(30))),
        Timestamp.from(now.plus(Duration.ofMinutes(35))),
        Timestamp.from(now));

    new IdentityRepository(jdbcTemplate)
        .touchSession(
            "c".repeat(64),
            now.plus(Duration.ofMinutes(10)),
            now.plus(Duration.ofMinutes(40)),
            now.plus(Duration.ofMinutes(5)));

    Instant idleExpiry =
        jdbcTemplate.queryForObject(
            "SELECT idle_expires_at FROM user_sessions WHERE id = ?",
            (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
            sessionId);
    assertThat(idleExpiry).isEqualTo(now.plus(Duration.ofMinutes(35)));
  }

  private JdbcTemplate jdbcTemplate() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    return new JdbcTemplate(dataSource);
  }
}
