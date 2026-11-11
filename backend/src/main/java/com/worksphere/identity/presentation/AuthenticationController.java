package com.worksphere.identity.presentation;

import com.worksphere.identity.application.AuthenticationService;
import com.worksphere.identity.domain.AuthenticatedUser;
import com.worksphere.shared.configuration.WorkSphereSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthenticationController {

  private final AuthenticationService authenticationService;
  private final boolean secureCookies;
  private final boolean googleEnabled;

  public AuthenticationController(
      AuthenticationService authenticationService,
      WorkSphereSecurityProperties securityProperties) {
    this.authenticationService = authenticationService;
    this.secureCookies = securityProperties.cookies().secure();
    this.googleEnabled = securityProperties.google().enabled();
  }

  @GetMapping("/auth/csrf")
  ResponseEntity<Void> csrf(CsrfToken csrfToken) {
    csrfToken.getToken();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/register")
  ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
    authenticationService.register(
        request.email(), request.password(), request.firstName(), request.lastName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new RegistrationResponse("Registration received."));
  }

  @PostMapping("/auth/login")
  ResponseEntity<UserResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    authenticationService.logout(readSessionCookie(servletRequest));
    AuthenticationService.LoginResult result =
        authenticationService.login(
            request.email(),
            request.password(),
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader(HttpHeaders.USER_AGENT));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            sessionCookie(result.sessionToken()).toString(),
            expiredCsrfCookie().toString())
        .body(UserResponse.from(result.user()));
  }

  @PostMapping("/auth/logout")
  ResponseEntity<Void> logout(HttpServletRequest request) {
    authenticationService.logout(readSessionCookie(request));
    return ResponseEntity.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            expiredSessionCookie().toString(),
            expiredCsrfCookie().toString())
        .build();
  }

  @GetMapping("/users/me")
  UserResponse me(Authentication authentication) {
    return UserResponse.from((AuthenticatedUser) authentication.getPrincipal());
  }

  @GetMapping("/auth/oauth2/google")
  ResponseEntity<Void> google() {
    if (!googleEnabled) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
        .location(URI.create("/oauth2/authorization/google"))
        .build();
  }

  private ResponseCookie sessionCookie(String value) {
    return ResponseCookie.from(secureCookies ? "__Host-WS-SESSION" : "WS-SESSION", value)
        .secure(secureCookies)
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofHours(12))
        .build();
  }

  private ResponseCookie expiredSessionCookie() {
    return ResponseCookie.from(secureCookies ? "__Host-WS-SESSION" : "WS-SESSION", "")
        .secure(secureCookies)
        .httpOnly(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  private ResponseCookie expiredCsrfCookie() {
    return ResponseCookie.from(secureCookies ? "__Host-WS-XSRF-TOKEN" : "WS-XSRF-TOKEN", "")
        .secure(secureCookies)
        .httpOnly(false)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  private String readSessionCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    String expected = secureCookies ? "__Host-WS-SESSION" : "WS-SESSION";
    return Arrays.stream(request.getCookies())
        .filter(cookie -> expected.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  public record RegistrationRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName) {}

  public record LoginRequest(
      @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}

  public record RegistrationResponse(String message) {}

  public record UserResponse(
      String id, String email, String firstName, String lastName, String displayName) {
    static UserResponse from(AuthenticatedUser user) {
      return new UserResponse(
          user.id().toString(),
          user.email(),
          user.firstName(),
          user.lastName(),
          user.displayName());
    }
  }
}
