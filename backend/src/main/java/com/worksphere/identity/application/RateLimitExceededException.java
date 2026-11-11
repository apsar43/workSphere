package com.worksphere.identity.application;

public class RateLimitExceededException extends RuntimeException {
  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super("Too many sign-in attempts. Try again later.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
