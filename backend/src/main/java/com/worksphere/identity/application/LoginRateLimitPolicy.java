package com.worksphere.identity.application;

import java.time.Duration;

public record LoginRateLimitPolicy(
    int accountMaximumAttempts,
    int sourceMaximumAttempts,
    Duration window,
    Duration blockDuration) {}
