package com.worksphere.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.identity.domain.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

  @Mock private IdentityStore store;
  @Mock private LoginRateLimiter loginRateLimiter;
  @Mock private PasswordEncoder passwordEncoder;

  private AuthenticationService service;

  @BeforeEach
  void setUp() {
    when(passwordEncoder.encode(any())).thenReturn("argon-hash");
    service =
        new AuthenticationService(
            store,
            loginRateLimiter,
            passwordEncoder,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new RegistrationPolicy(true),
            new SessionPolicy(
                java.time.Duration.ofMinutes(30),
                java.time.Duration.ofHours(12),
                java.time.Duration.ofMinutes(5)));
  }

  @Test
  void registersNormalizedVerifiedLocalIdentity() {
    service.register("  Apsar@Example.COM ", "secure-password", " Apsar ", " Khan ");

    verify(store)
        .createLocalUser(
            any(UUID.class),
            eq("Apsar@Example.COM"),
            eq("apsar@example.com"),
            eq("Apsar"),
            eq("Khan"),
            eq("argon-hash"),
            eq(true),
            eq(NOW));
  }

  @Test
  void keepsRegistrationGenericForExistingOrConcurrentAccount() {
    when(store.emailExists("apsar@example.com")).thenReturn(true);
    service.register("apsar@example.com", "secure-password", "Apsar", "Khan");
    verify(store, never())
        .createLocalUser(any(), any(), any(), any(), any(), any(), eq(true), any());

    when(store.emailExists("other@example.com")).thenReturn(false);
    org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
        .when(store)
        .createLocalUser(any(), any(), any(), any(), any(), any(), eq(true), any());
    service.register("other@example.com", "secure-password", "Other", "User");
  }

  @Test
  void createsOpaqueSessionForValidActiveCredentials() {
    AuthenticatedUser user = user();
    when(store.findCredentialUser("apsar@example.com"))
        .thenReturn(Optional.of(new IdentityStore.CredentialUser(user, "stored-hash", "ACTIVE")));
    when(passwordEncoder.matches("secure-password", "stored-hash")).thenReturn(true);

    AuthenticationService.LoginResult result =
        service.login("Apsar@Example.com", "secure-password", "127.0.0.1", "test-agent");

    assertThat(result.user()).isEqualTo(user);
    assertThat(result.sessionToken()).isNotBlank();
    verify(store)
        .createSession(
            any(UUID.class),
            eq(user.id()),
            eq(service.hashToken(result.sessionToken())),
            eq(NOW),
            eq(NOW.plusSeconds(1800)),
            eq(NOW.plusSeconds(43_200)),
            eq("127.0.0.1"),
            eq("test-agent"));
    verify(loginRateLimiter).recordSuccess("apsar@example.com");
  }

  @Test
  void rejectsUnknownWrongOrInactiveCredentialsWithSameMessage() {
    when(store.findCredentialUser("missing@example.com")).thenReturn(Optional.empty());
    assertGenericLoginFailure("missing@example.com", "wrong");

    when(store.findCredentialUser("apsar@example.com"))
        .thenReturn(
            Optional.of(new IdentityStore.CredentialUser(user(), "stored-hash", "DISABLED")));
    when(passwordEncoder.matches("right", "stored-hash")).thenReturn(true);
    assertGenericLoginFailure("apsar@example.com", "right");
  }

  @Test
  void revokesOnlyNonBlankSessionTokens() {
    service.logout(null);
    service.logout(" ");
    verify(store, never()).revokeSession(any(), any(), any());

    service.logout("raw-session-token");
    verify(store).revokeSession(service.hashToken("raw-session-token"), NOW, "USER_LOGOUT");
  }

  @Test
  void recordsFailedLoginAndRenewsAnActiveSessionAtAThrottledInterval() {
    when(store.findCredentialUser("missing@example.com")).thenReturn(Optional.empty());
    assertGenericLoginFailure("missing@example.com", "wrong");
    verify(loginRateLimiter).recordFailure("missing@example.com", "127.0.0.1");

    AuthenticatedUser user = user();
    String tokenHash = service.hashToken("raw-session-token");
    when(store.findActiveSession(tokenHash, NOW)).thenReturn(Optional.of(user));

    assertThat(service.authenticateSession("raw-session-token")).contains(user);
    verify(store).touchSession(tokenHash, NOW, NOW.plusSeconds(1800), NOW.minusSeconds(300));
  }

  private void assertGenericLoginFailure(String email, String password) {
    assertThatThrownBy(() -> service.login(email, password, "127.0.0.1", "agent"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Email or password is incorrect.");
  }

  private AuthenticatedUser user() {
    return new AuthenticatedUser(
        UUID.fromString("7787c953-a326-4d35-89b3-617691a487a6"),
        "apsar@example.com",
        "Apsar",
        "Khan",
        "Apsar Khan");
  }
}
