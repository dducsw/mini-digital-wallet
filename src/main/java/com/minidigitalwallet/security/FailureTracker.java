package com.minidigitalwallet.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;

/**
 * Counts failed attempts per key inside a fixed window and locks the key once the
 * threshold is reached. Updates are atomic per key (ConcurrentMap.compute), and the
 * cache is bounded: at most maxKeys entries, idle entries expire automatically.
 */
final class FailureTracker {

    private record Attempt(int failures, Instant windowStart, Instant blockedUntil) {
    }

    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Cache<String, Attempt> attempts;

    FailureTracker(int maxFailures, Duration window, Duration lockout, long maxKeys) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        Duration retention = window.compareTo(lockout) >= 0 ? window : lockout;
        this.attempts = Caffeine.newBuilder()
            .maximumSize(maxKeys)
            .expireAfterWrite(retention)
            .build();
    }

    boolean isBlocked(String key) {
        Attempt attempt = attempts.getIfPresent(key);
        return attempt != null
            && attempt.blockedUntil() != null
            && Instant.now().isBefore(attempt.blockedUntil());
    }

    void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.asMap().compute(key, (k, prev) -> {
            Attempt base = (prev == null || now.isAfter(prev.windowStart().plus(window)))
                ? new Attempt(0, now, null)
                : prev;
            int failures = base.failures() + 1;
            Instant blockedUntil = failures >= maxFailures ? now.plus(lockout) : null;
            return new Attempt(failures, base.windowStart(), blockedUntil);
        });
    }

    void reset(String key) {
        attempts.invalidate(key);
    }

    long trackedKeys() {
        attempts.cleanUp();
        return attempts.estimatedSize();
    }
}
