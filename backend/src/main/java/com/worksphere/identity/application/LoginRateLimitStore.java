package com.worksphere.identity.application;

import java.time.Duration;
import java.time.Instant;

public interface LoginRateLimitStore {
  boolean isBlocked(String accountKeyHash, String sourceKeyHash, Instant now);

  void recordFailure(
      String scope,
      String keyHash,
      int maximumAttempts,
      Duration window,
      Duration blockDuration,
      Instant now);

  void clearAccountFailures(String accountKeyHash);
}
