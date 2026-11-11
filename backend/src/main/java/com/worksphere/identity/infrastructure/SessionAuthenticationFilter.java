package com.worksphere.identity.infrastructure;

import com.worksphere.identity.application.AuthenticationService;
import com.worksphere.identity.domain.AuthenticatedUser;
import com.worksphere.shared.configuration.WorkSphereSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(
    name = "worksphere.identity.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private final AuthenticationService authenticationService;
  private final String cookieName;

  public SessionAuthenticationFilter(
      AuthenticationService authenticationService,
      WorkSphereSecurityProperties securityProperties) {
    this.authenticationService = authenticationService;
    this.cookieName = securityProperties.cookies().secure() ? "__Host-WS-SESSION" : "WS-SESSION";
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String rawToken = readCookie(request);
    if (rawToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticationService.authenticateSession(rawToken).ifPresent(user -> authenticate(user));
    }
    filterChain.doFilter(request, response);
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(cookie -> cookieName.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  private void authenticate(AuthenticatedUser user) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of()));
  }
}
