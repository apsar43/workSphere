package com.worksphere.identity.infrastructure;

import com.worksphere.identity.application.LoginRateLimitStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PostgresLoginRateLimitStore implements LoginRateLimitStore {

  private final JdbcTemplate jdbcTemplate;

  public PostgresLoginRateLimitStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean isBlocked(String accountKeyHash, String sourceKeyHash, Instant now) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM login_rate_limits
            WHERE ((scope = 'ACCOUNT' AND key_hash = ?)
                OR (scope = 'SOURCE' AND key_hash = ?))
              AND blocked_until > ?
            """,
            Integer.class,
            accountKeyHash,
            sourceKeyHash,
            Timestamp.from(now));
    return count != null && count > 0;
  }

  @Override
  public void recordFailure(
      String scope,
      String keyHash,
      int maximumAttempts,
      Duration window,
      Duration blockDuration,
      Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    Timestamp windowCutoff = Timestamp.from(now.minus(window));
    Timestamp blockUntil = Timestamp.from(now.plus(blockDuration));
    jdbcTemplate.update(
        """
        INSERT INTO login_rate_limits (
            scope, key_hash, failure_count, window_started_at, blocked_until, updated_at
        ) VALUES (?, ?, 1, ?, NULL, ?)
        ON CONFLICT (scope, key_hash) DO UPDATE SET
            failure_count = CASE
                WHEN login_rate_limits.window_started_at <= ? THEN 1
                ELSE login_rate_limits.failure_count + 1
            END,
            window_started_at = CASE
                WHEN login_rate_limits.window_started_at <= ? THEN ?
                ELSE login_rate_limits.window_started_at
            END,
            blocked_until = CASE
                WHEN (CASE
                    WHEN login_rate_limits.window_started_at <= ? THEN 1
                    ELSE login_rate_limits.failure_count + 1
                END) >= ? THEN ?
                ELSE login_rate_limits.blocked_until
            END,
            updated_at = ?
        """,
        scope,
        keyHash,
        timestamp,
        timestamp,
        windowCutoff,
        windowCutoff,
        timestamp,
        windowCutoff,
        maximumAttempts,
        blockUntil,
        timestamp);
  }

  @Override
  public void clearAccountFailures(String accountKeyHash) {
    jdbcTemplate.update(
        "DELETE FROM login_rate_limits WHERE scope = 'ACCOUNT' AND key_hash = ?", accountKeyHash);
  }
}
