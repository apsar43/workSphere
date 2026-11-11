package com.worksphere.shared.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("worksphere.security.session")
public record SessionProperties(
    Duration idleTimeout, Duration absoluteTimeout, Duration touchInterval) {
  public SessionProperties {
    idleTimeout = idleTimeout == null ? Duration.ofMinutes(30) : idleTimeout;
    absoluteTimeout = absoluteTimeout == null ? Duration.ofHours(12) : absoluteTimeout;
    touchInterval = touchInterval == null ? Duration.ofMinutes(5) : touchInterval;
  }
}
