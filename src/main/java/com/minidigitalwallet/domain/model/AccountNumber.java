package com.minidigitalwallet.domain.model;

import com.minidigitalwallet.exception.AppException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;

/**
 * 12-digit human account number (P2P recipient types this, not UUID).
 * Format: 8820 + 8 random digits. Spaces allowed on input, stripped here.
 */
public record AccountNumber(String digits) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountNumber {
        if (digits == null || !digits.matches("^[0-9]{12}$")) {
            throw new AppException("Account number must be 12 digits",
                HttpStatus.BAD_REQUEST, "invalid-account-number");
        }
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("\\s+", "");
    }

    public static AccountNumber of(String raw) {
        return new AccountNumber(normalize(raw));
    }

    public static String random() {
        return String.format("8820%08d", RANDOM.nextInt(100_000_000));
    }

    @Override
    public String toString() {
        return digits;
    }
}
