package com.worksphere.shared.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("worksphere.security")
public record WorkSphereSecurityProperties(
    boolean autoVerifyRegistration, Cookies cookies, Google google) {
  public WorkSphereSecurityProperties {
    cookies = cookies == null ? new Cookies(true) : cookies;
    google = google == null ? new Google(false) : google;
  }

  public record Cookies(boolean secure) {}

  public record Google(boolean enabled) {}
}
