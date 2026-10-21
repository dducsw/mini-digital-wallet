package com.minidigitalwallet.service.fee;

import org.springframework.stereotype.Component;

/**
 * Flat fee strategy: charges a fixed fee per transaction (e.g. 100 cents = $1.00 USD).
 */
@Component("FLAT")
public class FlatFeeStrategy implements FeeStrategy {

    public static final long DEFAULT_FLAT_FEE_CENTS = 100L;
    private final long flatFeeCents;

    public FlatFeeStrategy() {
        this(DEFAULT_FLAT_FEE_CENTS);
    }

    public FlatFeeStrategy(long flatFeeCents) {
        this.flatFeeCents = Math.max(0L, flatFeeCents);
    }

    @Override
    public long calculateFee(long amount) {
        return flatFeeCents;
    }
}
