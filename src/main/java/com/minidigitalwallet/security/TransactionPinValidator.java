package com.minidigitalwallet.security;

import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validates transaction PIN against user BCrypt hash (SRP).
 * PIN is mandatory for debits: user without PIN must set it first (403 pin-setup-required).
 * 5 failed attempts trigger 15-minute lock.
 */
@Component
@RequiredArgsConstructor
public class TransactionPinValidator {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinRateLimiter pinRateLimiter;

    public void validate(String userId, String providedPin) {
        if (userId == null || userId.isBlank()) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        UUID uid;
        try {
            uid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        User user = userRepository.findById(uid).orElse(null);
        if (user == null) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        if (user.getPinHash() == null) {
            throw new AppException(
                "Transaction PIN not set. Please set up your PIN first.",
                HttpStatus.FORBIDDEN,
                "pin-setup-required"
            );
        }

        if (pinRateLimiter.isBlocked(userId)) {
            throw new AppException(
                "Transaction PIN locked due to too many failed attempts. Try again in 15 minutes.",
                HttpStatus.TOO_MANY_REQUESTS,
                "pin-locked"
            );
        }

        if (providedPin == null || providedPin.isBlank()) {
            throw new AppException(
                "Transaction PIN is required for this operation",
                HttpStatus.UNAUTHORIZED,
                "pin-required"
            );
        }

        if (!passwordEncoder.matches(providedPin, user.getPinHash())) {
            pinRateLimiter.recordFailure(userId);
            throw new AppException(
                "Invalid transaction PIN",
                HttpStatus.UNAUTHORIZED,
                "pin-invalid"
            );
        }

        pinRateLimiter.reset(userId);
    }
}
