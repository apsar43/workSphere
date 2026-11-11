package com.worksphere.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.identity.application.RateLimitExceededException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ValidationTestController())
            .setControllerAdvice(new GlobalExceptionHandler(clock))
            .setValidator(validator)
            .build();
  }

  @Test
  void returnsConsistentValidationError() throws Exception {
    mockMvc
        .perform(
            post("/validation-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.path").value("/validation-test"))
        .andExpect(jsonPath("$.violations[0].field").value("name"));
  }

  @Test
  void returnsRetryAfterForRateLimitedLogin() throws Exception {
    mockMvc
        .perform(post("/rate-limit-test"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "900"))
        .andExpect(jsonPath("$.message").value("Too many sign-in attempts. Try again later."));
  }

  @RestController
  static class ValidationTestController {

    @PostMapping("/validation-test")
    void validate(@Valid @RequestBody ValidationRequest request) {}

    @PostMapping("/rate-limit-test")
    void rateLimited() {
      throw new RateLimitExceededException(900);
    }
  }

  record ValidationRequest(@NotBlank String name) {}
}
