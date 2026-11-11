package com.worksphere.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtcClockServiceTest {

  @Mock private Clock clock;

  @Test
  void returnsTimeFromInjectedClock() {
    Instant expected = Instant.parse("2026-01-02T03:04:05Z");
    when(clock.instant()).thenReturn(expected);

    UtcClockService service = new UtcClockService(clock);

    assertThat(service.now()).isEqualTo(expected);
  }
}
