package com.worksphere.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.identity.application.AuthenticationService;
import com.worksphere.identity.domain.AuthenticatedUser;
import com.worksphere.shared.configuration.WorkSphereSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAuthenticationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesAValidOpaqueSessionCookie() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedUser user =
        new AuthenticatedUser(
            UUID.randomUUID(), "apsar@example.com", "Apsar", "Khan", "Apsar Khan");
    when(authenticationService.authenticateSession("raw-token")).thenReturn(Optional.of(user));
    SessionAuthenticationFilter filter =
        new SessionAuthenticationFilter(authenticationService, properties(false));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("WS-SESSION", "raw-token"));
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(user);
    verify(chain).doFilter(request, response);
  }

  @Test
  void passesThroughWithoutSessionCookie() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    SessionAuthenticationFilter filter =
        new SessionAuthenticationFilter(authenticationService, properties(true));
    MockHttpServletRequest request = new MockHttpServletRequest();
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  private WorkSphereSecurityProperties properties(boolean secure) {
    return new WorkSphereSecurityProperties(
        false,
        new WorkSphereSecurityProperties.Cookies(secure),
        new WorkSphereSecurityProperties.Google(false));
  }
}
