package com.worksphere.identity.infrastructure;

import com.worksphere.identity.application.IdentityStore;
import com.worksphere.identity.domain.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdentityRepository implements IdentityStore {

  private final JdbcTemplate jdbcTemplate;

  public IdentityRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean emailExists(String normalizedEmail) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE normalized_email = ?",
            Integer.class,
            normalizedEmail);
    return count != null && count > 0;
  }

  @Override
  @Transactional
  public void createLocalUser(
      UUID id,
      String email,
      String normalizedEmail,
      String firstName,
      String lastName,
      String passwordHash,
      boolean verified,
      Instant now) {
    jdbcTemplate.update(
        """
        INSERT INTO users (
            id, email, normalized_email, first_name, last_name, display_name, status,
            email_verified_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """,
        id,
        email,
        normalizedEmail,
        firstName,
        lastName,
        firstName + " " + lastName,
        verified ? "ACTIVE" : "PENDING_VERIFICATION",
        verified ? Timestamp.from(now) : null,
        Timestamp.from(now),
        Timestamp.from(now));
    jdbcTemplate.update(
        "INSERT INTO password_credentials (user_id, password_hash, created_at, changed_at) VALUES (?, ?, ?, ?)",
        id,
        passwordHash,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  @Override
  public Optional<CredentialUser> findCredentialUser(String normalizedEmail) {
    return jdbcTemplate
        .query(
            """
            SELECT u.id, u.email, u.first_name, u.last_name, u.display_name, u.status,
                   p.password_hash
            FROM users u
            JOIN password_credentials p ON p.user_id = u.id
            WHERE u.normalized_email = ?
            """,
            (resultSet, rowNumber) ->
                new CredentialUser(
                    new AuthenticatedUser(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("email"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("display_name")),
                    resultSet.getString("password_hash"),
                    resultSet.getString("status")),
            normalizedEmail)
        .stream()
        .findFirst();
  }

  @Override
  public void createSession(
      UUID id,
      UUID userId,
      String tokenHash,
      Instant now,
      Instant idleExpiry,
      Instant absoluteExpiry,
      String ipAddress,
      String userAgent) {
    jdbcTemplate.update(
        """
        INSERT INTO user_sessions (
            id, user_id, token_hash, created_at, idle_expires_at, absolute_expires_at,
            last_seen_at, ip_address, user_agent
        ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS inet), ?)
        """,
        id,
        userId,
        tokenHash,
        Timestamp.from(now),
        Timestamp.from(idleExpiry),
        Timestamp.from(absoluteExpiry),
        Timestamp.from(now),
        ipAddress,
        userAgent);
  }

  @Override
  public Optional<AuthenticatedUser> findActiveSession(String tokenHash, Instant now) {
    return jdbcTemplate
        .query(
            """
            SELECT u.id, u.email, u.first_name, u.last_name, u.display_name
            FROM user_sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token_hash = ?
              AND s.revoked_at IS NULL
              AND s.idle_expires_at > ?
              AND s.absolute_expires_at > ?
              AND u.status = 'ACTIVE'
            """,
            (resultSet, rowNumber) ->
                new AuthenticatedUser(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("email"),
                    resultSet.getString("first_name"),
                    resultSet.getString("last_name"),
                    resultSet.getString("display_name")),
            tokenHash,
            Timestamp.from(now),
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  @Override
  public void touchSession(
      String tokenHash, Instant now, Instant idleExpiry, Instant touchThreshold) {
    jdbcTemplate.update(
        """
        UPDATE user_sessions
        SET last_seen_at = ?,
            idle_expires_at = LEAST(?, absolute_expires_at)
        WHERE token_hash = ?
          AND revoked_at IS NULL
          AND idle_expires_at > ?
          AND absolute_expires_at > ?
          AND last_seen_at <= ?
        """,
        Timestamp.from(now),
        Timestamp.from(idleExpiry),
        tokenHash,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(touchThreshold));
  }

  @Override
  public void revokeSession(String tokenHash, Instant now, String reason) {
    jdbcTemplate.update(
        """
        UPDATE user_sessions
        SET revoked_at = ?, revocation_reason = ?
        WHERE token_hash = ? AND revoked_at IS NULL
        """,
        Timestamp.from(now),
        reason,
        tokenHash);
  }
}
