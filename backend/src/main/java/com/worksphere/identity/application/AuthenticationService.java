package com.worksphere.identity.application;

import com.worksphere.identity.domain.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthenticationService {

  public record LoginResult(AuthenticatedUser user, String sessionToken) {}

  private final IdentityStore repository;
  private final LoginRateLimiter loginRateLimiter;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SessionPolicy sessionPolicy;
  private final boolean autoVerifyRegistration;
  private final String dummyPasswordHash;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthenticationService(
      IdentityStore repository,
      LoginRateLimiter loginRateLimiter,
      PasswordEncoder passwordEncoder,
      Clock clock,
      RegistrationPolicy registrationPolicy,
      SessionPolicy sessionPolicy) {
    this.repository = repository;
    this.loginRateLimiter = loginRateLimiter;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.autoVerifyRegistration = registrationPolicy.autoVerifyRegistration();
    this.sessionPolicy = sessionPolicy;
    this.dummyPasswordHash = passwordEncoder.encode(randomToken());
  }

  public void register(String email, String password, String firstName, String lastName) {
    String normalizedEmail = normalizeEmail(email);
    String passwordHash = passwordEncoder.encode(password);
    if (repository.emailExists(normalizedEmail)) {
      return;
    }
    Instant now = clock.instant();
    try {
      repository.createLocalUser(
          UUID.randomUUID(),
          email.trim(),
          normalizedEmail,
          firstName.trim(),
          lastName.trim(),
          passwordHash,
          autoVerifyRegistration,
          now);
    } catch (DuplicateKeyException duplicateRegistration) {
      // Keep registration idempotent and avoid confirming whether an account exists.
    }
  }

  public LoginResult login(String email, String password, String ipAddress, String userAgent) {
    String normalizedEmail = normalizeEmail(email);
    loginRateLimiter.checkAllowed(normalizedEmail, ipAddress);
    Optional<IdentityStore.CredentialUser> candidate =
        repository.findCredentialUser(normalizedEmail);
    String hash =
        candidate.map(IdentityStore.CredentialUser::passwordHash).orElse(dummyPasswordHash);
    boolean passwordMatches = passwordEncoder.matches(password, hash);
    if (candidate.isEmpty() || !passwordMatches || !candidate.get().status().equals("ACTIVE")) {
      loginRateLimiter.recordFailure(normalizedEmail, ipAddress);
      throw new AuthenticationException("Email or password is incorrect.");
    }
    IdentityStore.CredentialUser credential = candidate.get();
    loginRateLimiter.recordSuccess(normalizedEmail);

    Instant now = clock.instant();
    String rawToken = randomToken();
    repository.createSession(
        UUID.randomUUID(),
        credential.user().id(),
        hashToken(rawToken),
        now,
        now.plus(sessionPolicy.idleTimeout()),
        now.plus(sessionPolicy.absoluteTimeout()),
        ipAddress,
        userAgent);
    return new LoginResult(credential.user(), rawToken);
  }

  public Optional<AuthenticatedUser> authenticateSession(String rawToken) {
    Instant now = clock.instant();
    String tokenHash = hashToken(rawToken);
    Optional<AuthenticatedUser> user = repository.findActiveSession(tokenHash, now);
    user.ifPresent(
        ignored ->
            repository.touchSession(
                tokenHash,
                now,
                now.plus(sessionPolicy.idleTimeout()),
                now.minus(sessionPolicy.touchInterval())));
    return user;
  }

  public void logout(String rawToken) {
    if (rawToken != null && !rawToken.isBlank()) {
      repository.revokeSession(hashToken(rawToken), clock.instant(), "USER_LOGOUT");
    }
  }

  public String hashToken(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
