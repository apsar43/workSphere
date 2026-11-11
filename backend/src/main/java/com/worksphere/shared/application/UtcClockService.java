package com.worksphere.shared.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class UtcClockService {

  private final Clock clock;

  public UtcClockService(Clock clock) {
    this.clock = clock;
  }

  public Instant now() {
    return clock.instant();
  }
}
