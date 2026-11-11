package com.worksphere.shared.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class FoundationSecurityConfiguration {

  @Bean
  SecurityFilterChain foundationSecurityFilterChain(HttpSecurity http) throws Exception {
    RequestMatcher publicActuatorEndpoint =
        request ->
            request.getRequestURI().equals("/actuator/health")
                || request.getRequestURI().equals("/actuator/info")
                || request.getRequestURI().equals("/api/v1/actuator/health")
                || request.getRequestURI().equals("/api/v1/actuator/info");

    return http.authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(publicActuatorEndpoint)
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .build();
  }
}
