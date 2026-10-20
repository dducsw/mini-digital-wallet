package com.minidigitalwallet.security;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Brute-force guard for the 6-digit transaction PIN.
 * Policy: 5 failed attempts -> blocked for 15 minutes. Keyed by userId.
 * Single-instance only: for multi-replica deployments use a shared store (e.g. Redis).
 */
@Component
public class PinRateLimiter {

    private final FailureTracker tracker = new FailureTracker(
        5, Duration.ofMinutes(15), Duration.ofMinutes(15), 10_000);

    public boolean isBlocked(String userId) {
        return userId != null && tracker.isBlocked(userId);
    }

    public void recordFailure(String userId) {
        if (userId != null) {
            tracker.recordFailure(userId);
        }
    }

    public void reset(String userId) {
        if (userId != null) {
            tracker.reset(userId);
        }
    }
}
