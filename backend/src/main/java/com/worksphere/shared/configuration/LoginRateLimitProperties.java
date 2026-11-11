package com.worksphere.shared.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("worksphere.security.login-rate-limit")
public record LoginRateLimitProperties(
    int accountMaximumAttempts,
    int sourceMaximumAttempts,
    Duration window,
    Duration blockDuration) {
  public LoginRateLimitProperties {
    accountMaximumAttempts = accountMaximumAttempts > 0 ? accountMaximumAttempts : 5;
    sourceMaximumAttempts = sourceMaximumAttempts > 0 ? sourceMaximumAttempts : 100;
    window = window == null ? Duration.ofMinutes(10) : window;
    blockDuration = blockDuration == null ? Duration.ofMinutes(15) : blockDuration;
  }
}
