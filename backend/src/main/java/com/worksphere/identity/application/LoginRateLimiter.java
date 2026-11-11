package com.worksphere.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LoginRateLimiter {
  static final String ACCOUNT_SCOPE = "ACCOUNT";
  static final String SOURCE_SCOPE = "SOURCE";

  private final LoginRateLimitStore store;
  private final LoginRateLimitPolicy policy;
  private final Clock clock;

  public LoginRateLimiter(LoginRateLimitStore store, LoginRateLimitPolicy policy, Clock clock) {
    this.store = store;
    this.policy = policy;
    this.clock = clock;
  }

  public void checkAllowed(String normalizedEmail, String sourceAddress) {
    Instant now = clock.instant();
    if (store.isBlocked(accountKey(normalizedEmail), sourceKey(sourceAddress), now)) {
      throw new RateLimitExceededException(policy.blockDuration().toSeconds());
    }
  }

  public void recordFailure(String normalizedEmail, String sourceAddress) {
    Instant now = clock.instant();
    store.recordFailure(
        ACCOUNT_SCOPE,
        accountKey(normalizedEmail),
        policy.accountMaximumAttempts(),
        policy.window(),
        policy.blockDuration(),
        now);
    store.recordFailure(
        SOURCE_SCOPE,
        sourceKey(sourceAddress),
        policy.sourceMaximumAttempts(),
        policy.window(),
        policy.blockDuration(),
        now);
  }

  public void recordSuccess(String normalizedEmail) {
    store.clearAccountFailures(accountKey(normalizedEmail));
  }

  private String accountKey(String normalizedEmail) {
    return hash("account:" + normalizedEmail);
  }

  private String sourceKey(String sourceAddress) {
    return hash("source:" + (sourceAddress == null ? "unknown" : sourceAddress));
  }

  private String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
