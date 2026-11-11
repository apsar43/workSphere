package com.worksphere.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.identity.application.AuthenticationService;
import com.worksphere.identity.domain.AuthenticatedUser;
import com.worksphere.shared.configuration.WorkSphereSecurityProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;

class AuthenticationControllerTest {

  private final AuthenticationService service = mock(AuthenticationService.class);

  @Test
  void materializesCsrfAndRegistersUser() {
    AuthenticationController controller = controller(false, false);
    CsrfToken csrfToken = mock(CsrfToken.class);
    when(csrfToken.getToken()).thenReturn("csrf-token");

    assertThat(controller.csrf(csrfToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var response =
        controller.register(
            new AuthenticationController.RegistrationRequest(
                "apsar@example.com", "secure-password", "Apsar", "Khan"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().message()).isEqualTo("Registration received.");
    verify(service).register("apsar@example.com", "secure-password", "Apsar", "Khan");
  }

  @Test
  void loginSetsLocalSessionAndRotatesCsrfCookie() {
    AuthenticationController controller = controller(false, false);
    AuthenticatedUser user = user();
    when(service.login(eq("apsar@example.com"), eq("secure-password"), any(), eq("agent")))
        .thenReturn(new AuthenticationService.LoginResult(user, "raw-token"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader(HttpHeaders.USER_AGENT, "agent");

    var response =
        controller.login(
            new AuthenticationController.LoginRequest("apsar@example.com", "secure-password"),
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().email()).isEqualTo("apsar@example.com");
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .anyMatch(cookie -> cookie.startsWith("WS-SESSION=raw-token"))
        .anyMatch(cookie -> cookie.startsWith("WS-XSRF-TOKEN=;"));
  }

  @Test
  void logoutRevokesCookieAndCurrentUserIsMapped() {
    AuthenticationController controller = controller(false, false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new jakarta.servlet.http.Cookie("WS-SESSION", "raw-token"));

    var logout = controller.logout(request);
    verify(service).logout("raw-token");
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(logout.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2);

    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(user(), null, java.util.List.of());
    assertThat(controller.me(authentication).displayName()).isEqualTo("Apsar Khan");
  }

  @Test
  void googleEntryPointIsDisabledUntilConfiguredAndRedirectsWhenEnabled() {
    assertThat(controller(false, false).google().getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    var enabled = controller(true, true).google();
    assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
    assertThat(enabled.getHeaders().getLocation().toString())
        .isEqualTo("/oauth2/authorization/google");
  }

  private AuthenticationController controller(boolean secure, boolean googleEnabled) {
    return new AuthenticationController(
        service,
        new WorkSphereSecurityProperties(
            false,
            new WorkSphereSecurityProperties.Cookies(secure),
            new WorkSphereSecurityProperties.Google(googleEnabled)));
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
