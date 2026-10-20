package com.minidigitalwallet.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Brute-force guard for login. Policy: 5 failed attempts in 15 min -> block 15 min. Success resets.
 * Keyed by normalized username.
 * Single-instance only: state is local, lost on restart, not shared across
 * replicas. For multi-replica deployments use a shared store (e.g. Redis).
 */
@Component
public class LoginRateLimiter {

    private final FailureTracker tracker = new FailureTracker(
        5, Duration.ofMinutes(15), Duration.ofMinutes(15), 100_000);

    public boolean isBlocked(String username) {
        return tracker.isBlocked(normalize(username));
    }

    public void recordFailure(String username) {
        tracker.recordFailure(normalize(username));
    }

    public void reset(String username) {
        tracker.reset(normalize(username));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
