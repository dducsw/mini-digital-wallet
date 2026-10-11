package com.minidigitalwallet.domain.model;

import com.minidigitalwallet.exception.AppException;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable Value Object representing monetary amount in minor units (cents) with currency.
 * Enforces financial domain constraints: non-negative amount and identical currency operations.
 */
public record Money(Long amountInCents, String currency) implements Serializable, Comparable<Money> {

    public static final String DEFAULT_CURRENCY = "USD";

    public Money {
        if (amountInCents == null) {
            throw new AppException("Amount cannot be null", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        if (amountInCents < 0) {
            throw new AppException("Amount cannot be negative", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        } else {
            currency = currency.trim().toUpperCase();
        }
    }

    public static Money of(Long amountInCents, String currency) {
        return new Money(amountInCents, currency);
    }

    public static Money ofUsd(Long amountInCents) {
        return new Money(amountInCents, DEFAULT_CURRENCY);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        validateSameCurrency(other);
        return new Money(this.amountInCents + other.amountInCents, this.currency);
    }

    public Money minus(Money other) {
        validateSameCurrency(other);
        if (this.amountInCents < other.amountInCents) {
            throw new com.minidigitalwallet.exception.InsufficientFundsException(
                "Insufficient funds: have " + this.amountInCents + " " + this.currency +
                ", required " + other.amountInCents + " " + this.currency
            );
        }
        return new Money(this.amountInCents - other.amountInCents, this.currency);
    }

    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return this.amountInCents > other.amountInCents;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        validateSameCurrency(other);
        return this.amountInCents >= other.amountInCents;
    }

    public boolean isZero() {
        return this.amountInCents == 0L;
    }

    public void validateSameCurrency(Money other) {
        validateCurrency(other == null ? null : other.currency);
    }

    public void validateCurrency(String otherCurrency) {
        if (!Objects.equals(this.currency, otherCurrency)) {
            throw new AppException(
                "Currency mismatch: " + this.currency + " vs " + otherCurrency,
                HttpStatus.BAD_REQUEST,
                "currency-mismatch"
            );
        }
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return Long.compare(this.amountInCents, other.amountInCents);
    }

    @Override
    public String toString() {
        return amountInCents + " " + currency;
    }
}
