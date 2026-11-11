package com.worksphere.shared.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("worksphere.cors")
public record WorkSphereCorsProperties(List<String> allowedOrigins) {
  public WorkSphereCorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
