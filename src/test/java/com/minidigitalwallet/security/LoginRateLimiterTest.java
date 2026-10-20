package com.minidigitalwallet.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void locksUsernameAfterFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("alice");
        }
        assertThat(limiter.isBlocked("alice")).isFalse();

        limiter.recordFailure("alice");
        assertThat(limiter.isBlocked("alice")).isTrue();
    }

    @Test
    void usernameIsCaseInsensitiveAndTrimmed() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("  Alice ");
        }

        assertThat(limiter.isBlocked("alice")).isTrue();
    }

    @Test
    void successfulLoginResetsFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("bob");
        }
        limiter.reset("bob");
        limiter.recordFailure("bob");

        assertThat(limiter.isBlocked("bob")).isFalse();
    }
}
