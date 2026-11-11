package com.worksphere.identity.application;

import java.time.Duration;

public record SessionPolicy(
    Duration idleTimeout, Duration absoluteTimeout, Duration touchInterval) {}
