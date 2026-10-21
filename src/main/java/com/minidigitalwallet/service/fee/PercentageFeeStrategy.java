package com.minidigitalwallet.service.fee;

import org.springframework.stereotype.Component;

/**
 * Percentage fee strategy: charges a proportional fee (e.g. 1.5% rounded up).
 */
@Component("PERCENTAGE")
public class PercentageFeeStrategy implements FeeStrategy {

    public static final double DEFAULT_RATE = 0.015; // 1.5%
    private final double rate;

    public PercentageFeeStrategy() {
        this(DEFAULT_RATE);
    }

    public PercentageFeeStrategy(double rate) {
        if (rate < 0.0) {
            throw new IllegalArgumentException("Fee rate cannot be negative");
        }
        this.rate = rate;
    }

    @Override
    public long calculateFee(long amount) {
        if (amount <= 0) {
            return 0L;
        }
        return (long) Math.ceil(amount * rate);
    }
}
