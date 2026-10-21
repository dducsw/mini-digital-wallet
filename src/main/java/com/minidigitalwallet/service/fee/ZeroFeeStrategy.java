package com.minidigitalwallet.service.fee;

import org.springframework.stereotype.Component;

/**
 * Default fee strategy: zero fee (100% free transactions).
 */
@Component("ZERO")
public class ZeroFeeStrategy implements FeeStrategy {

    @Override
    public long calculateFee(long amount) {
        return 0L;
    }
}
