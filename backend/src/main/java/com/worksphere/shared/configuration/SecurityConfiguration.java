package com.worksphere.shared.configuration;

import com.worksphere.identity.application.LoginRateLimitPolicy;
import com.worksphere.identity.application.RegistrationPolicy;
import com.worksphere.identity.application.SessionPolicy;
import com.worksphere.identity.infrastructure.SessionAuthenticationFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({
  WorkSphereSecurityProperties.class,
  WorkSphereCorsProperties.class,
  SessionProperties.class,
  LoginRateLimitProperties.class
})
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SessionAuthenticationFilter sessionAuthenticationFilter,
      CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    return http.authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/oauth2/google")
                    .permitAll()
                    .requestMatchers("/api/v1/users/me", "/api/v1/auth/logout")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(csrfHandler))
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, exception) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value())))
        .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class)
        .build();
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository(WorkSphereSecurityProperties securityProperties) {
    boolean secureCookies = securityProperties.cookies().secure();
    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setCookieName(secureCookies ? "__Host-WS-XSRF-TOKEN" : "WS-XSRF-TOKEN");
    repository.setHeaderName("X-XSRF-TOKEN");
    repository.setCookieCustomizer(
        cookie ->
            cookie
                .secure(secureCookies)
                .httpOnly(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(12)));
    return repository;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
  }

  @Bean
  RegistrationPolicy registrationPolicy(WorkSphereSecurityProperties securityProperties) {
    return new RegistrationPolicy(securityProperties.autoVerifyRegistration());
  }

  @Bean
  SessionPolicy sessionPolicy(SessionProperties properties) {
    return new SessionPolicy(
        properties.idleTimeout(), properties.absoluteTimeout(), properties.touchInterval());
  }

  @Bean
  LoginRateLimitPolicy loginRateLimitPolicy(LoginRateLimitProperties properties) {
    return new LoginRateLimitPolicy(
        properties.accountMaximumAttempts(),
        properties.sourceMaximumAttempts(),
        properties.window(),
        properties.blockDuration());
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(WorkSphereCorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    source.registerCorsConfiguration("/actuator/**", configuration);
    return source;
  }
}
