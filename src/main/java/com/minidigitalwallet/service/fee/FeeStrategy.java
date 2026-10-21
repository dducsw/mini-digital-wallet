package com.minidigitalwallet.service.fee;

/**
 * Strategy interface for calculating transaction fees.
 * Follows the Open/Closed Principle (OCP): new fee algorithms can be added
 * by creating new implementations without altering existing core transaction code.
 */
public interface FeeStrategy {

    /**
     * Calculates the fee in minor units (cents) for a given transaction amount.
     *
     * @param amount Transaction amount in minor units (cents).
     * @return Fee amount in minor units (must be >= 0).
     */
    long calculateFee(long amount);
}
