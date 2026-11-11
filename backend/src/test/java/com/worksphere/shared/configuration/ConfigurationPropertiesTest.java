package com.worksphere.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationPropertiesTest {

  @Test
  void suppliesSecureSessionAndRateLimitDefaults() {
    SessionProperties sessions = new SessionProperties(null, null, null);
    assertThat(sessions.idleTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(sessions.absoluteTimeout()).isEqualTo(Duration.ofHours(12));
    assertThat(sessions.touchInterval()).isEqualTo(Duration.ofMinutes(5));

    LoginRateLimitProperties rateLimits = new LoginRateLimitProperties(0, -1, null, null);
    assertThat(rateLimits.accountMaximumAttempts()).isEqualTo(5);
    assertThat(rateLimits.sourceMaximumAttempts()).isEqualTo(100);
    assertThat(rateLimits.window()).isEqualTo(Duration.ofMinutes(10));
    assertThat(rateLimits.blockDuration()).isEqualTo(Duration.ofMinutes(15));

    WorkSphereSecurityProperties security = new WorkSphereSecurityProperties(false, null, null);
    assertThat(security.cookies().secure()).isTrue();
    assertThat(security.google().enabled()).isFalse();
    assertThat(new WorkSphereCorsProperties(null).allowedOrigins()).isEmpty();
  }

  @Test
  void preservesExplicitProductionOverridesAndDefensivelyCopiesOrigins() {
    SessionProperties sessions =
        new SessionProperties(Duration.ofMinutes(20), Duration.ofHours(8), Duration.ofMinutes(2));
    assertThat(sessions.idleTimeout()).isEqualTo(Duration.ofMinutes(20));
    assertThat(sessions.absoluteTimeout()).isEqualTo(Duration.ofHours(8));
    assertThat(sessions.touchInterval()).isEqualTo(Duration.ofMinutes(2));

    LoginRateLimitProperties rateLimits =
        new LoginRateLimitProperties(4, 50, Duration.ofMinutes(5), Duration.ofMinutes(30));
    assertThat(rateLimits.accountMaximumAttempts()).isEqualTo(4);
    assertThat(rateLimits.sourceMaximumAttempts()).isEqualTo(50);
    assertThat(rateLimits.window()).isEqualTo(Duration.ofMinutes(5));
    assertThat(rateLimits.blockDuration()).isEqualTo(Duration.ofMinutes(30));

    assertThat(new WorkSphereCorsProperties(List.of("https://worksphere.example")).allowedOrigins())
        .containsExactly("https://worksphere.example");
  }
}
