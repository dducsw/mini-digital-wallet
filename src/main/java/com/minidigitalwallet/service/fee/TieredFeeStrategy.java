package com.minidigitalwallet.service.fee;

import org.springframework.stereotype.Component;

/**
 * Tiered fee strategy: free for small transactions (<= $100.00 / 10,000 cents),
 * flat fee for larger amounts.
 */
@Component("TIERED")
public class TieredFeeStrategy implements FeeStrategy {

    public static final long DEFAULT_THRESHOLD_CENTS = 10_000L; // $100.00
    public static final long DEFAULT_TIERED_FEE_CENTS = 50L;     // $0.50

    private final long thresholdCents;
    private final long tieredFeeCents;

    public TieredFeeStrategy() {
        this(DEFAULT_THRESHOLD_CENTS, DEFAULT_TIERED_FEE_CENTS);
    }

    public TieredFeeStrategy(long thresholdCents, long tieredFeeCents) {
        this.thresholdCents = thresholdCents;
        this.tieredFeeCents = Math.max(0L, tieredFeeCents);
    }

    @Override
    public long calculateFee(long amount) {
        return amount > thresholdCents ? tieredFeeCents : 0L;
    }
}
