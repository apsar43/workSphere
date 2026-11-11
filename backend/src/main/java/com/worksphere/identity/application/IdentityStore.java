package com.worksphere.identity.application;

import com.worksphere.identity.domain.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityStore {
  record CredentialUser(AuthenticatedUser user, String passwordHash, String status) {}

  boolean emailExists(String normalizedEmail);

  void createLocalUser(
      UUID id,
      String email,
      String normalizedEmail,
      String firstName,
      String lastName,
      String passwordHash,
      boolean verified,
      Instant now);

  Optional<CredentialUser> findCredentialUser(String normalizedEmail);

  void createSession(
      UUID id,
      UUID userId,
      String tokenHash,
      Instant now,
      Instant idleExpiry,
      Instant absoluteExpiry,
      String ipAddress,
      String userAgent);

  Optional<AuthenticatedUser> findActiveSession(String tokenHash, Instant now);

  void touchSession(String tokenHash, Instant now, Instant idleExpiry, Instant touchThreshold);

  void revokeSession(String tokenHash, Instant now, String reason);
}
