package com.minidigitalwallet.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FailureTrackerTest {

    @Test
    void locksExactlyAtThreshold() {
        FailureTracker tracker = new FailureTracker(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 100);

        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("alice");
        }
        assertThat(tracker.isBlocked("alice")).isFalse();

        tracker.recordFailure("alice");
        assertThat(tracker.isBlocked("alice")).isTrue();
    }

    @Test
    void resetClearsFailures() {
        FailureTracker tracker = new FailureTracker(3, Duration.ofMinutes(15), Duration.ofMinutes(15), 100);
        tracker.recordFailure("bob");
        tracker.recordFailure("bob");
        tracker.reset("bob");
        tracker.recordFailure("bob");

        assertThat(tracker.isBlocked("bob")).isFalse();
    }

    @Test
    void concurrentFailuresAreNotLost() throws InterruptedException {
        int threads = 50;
        FailureTracker tracker = new FailureTracker(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 100);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    tracker.recordFailure("carol");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(tracker.isBlocked("carol")).isTrue();
    }

    @Test
    void trackedKeysStayWithinMaxKeys() {
        FailureTracker tracker = new FailureTracker(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 100);
        for (int i = 0; i < 10_000; i++) {
            tracker.recordFailure("attacker-" + i);
        }

        assertThat(tracker.trackedKeys()).isLessThanOrEqualTo(100);
    }
}
