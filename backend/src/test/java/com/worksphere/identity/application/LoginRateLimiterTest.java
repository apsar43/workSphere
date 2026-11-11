package com.worksphere.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

  private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

  @Mock private LoginRateLimitStore store;
  private LoginRateLimiter limiter;

  @BeforeEach
  void setUp() {
    limiter =
        new LoginRateLimiter(
            store,
            new LoginRateLimitPolicy(5, 100, Duration.ofMinutes(10), Duration.ofMinutes(15)),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void rejectsAKeyThatIsCurrentlyBlocked() {
    when(store.isBlocked(anyString(), anyString(), eq(NOW))).thenReturn(true);
    assertThatThrownBy(() -> limiter.checkAllowed("apsar@example.com", "127.0.0.1"))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage("Too many sign-in attempts. Try again later.");
  }

  @Test
  void recordsAccountAndSourceFailuresWithoutPersistingRawIdentifiers() {
    limiter.recordFailure("apsar@example.com", "127.0.0.1");

    verify(store)
        .recordFailure(
            eq(LoginRateLimiter.ACCOUNT_SCOPE),
            anyString(),
            eq(5),
            eq(Duration.ofMinutes(10)),
            eq(Duration.ofMinutes(15)),
            eq(NOW));
    verify(store)
        .recordFailure(
            eq(LoginRateLimiter.SOURCE_SCOPE),
            anyString(),
            eq(100),
            eq(Duration.ofMinutes(10)),
            eq(Duration.ofMinutes(15)),
            eq(NOW));
  }

  @Test
  void successfulLoginClearsOnlyTheAccountCounter() {
    limiter.recordSuccess("apsar@example.com");
    verify(store).clearAccountFailures(anyString());
  }
}
