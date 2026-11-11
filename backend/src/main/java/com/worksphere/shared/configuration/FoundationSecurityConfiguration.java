package com.worksphere.shared.configuration;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${worksphere.cors.allowed-origins}") List<String> allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    source.registerCorsConfiguration("/actuator/**", configuration);
    return source;
  }
}
