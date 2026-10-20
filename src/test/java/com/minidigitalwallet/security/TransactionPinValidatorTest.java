package com.minidigitalwallet.security;

import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionPinValidatorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PinRateLimiter pinRateLimiter;
    private TransactionPinValidator validator;

    private final UUID userId = UUID.randomUUID();
    private User userWithPin;
    private User userWithoutPin;

    @BeforeEach
    void setUp() {
        pinRateLimiter = new PinRateLimiter();
        validator = new TransactionPinValidator(userRepository, passwordEncoder, pinRateLimiter);

        userWithPin = User.builder()
            .id(userId)
            .username("alice")
            .pinHash("$2a$10$hashedpin")
            .build();

        userWithoutPin = User.builder()
            .id(userId)
            .username("bob")
            .pinHash(null)
            .build();
    }

    @Test
    @DisplayName("User without PIN must set it up first (403 pin-setup-required)")
    void validate_UserWithoutPin_ThrowsSetupRequired() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithoutPin));

        assertThatThrownBy(() -> validator.validate(userId.toString(), null))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(appEx.getErrorCode()).isEqualTo("pin-setup-required");
            });
    }

    @Test
    @DisplayName("User with PIN throws 401 when PIN is missing")
    void validate_MissingPin_ThrowsUnauthorized() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPin));

        assertThatThrownBy(() -> validator.validate(userId.toString(), null))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(appEx.getErrorCode()).isEqualTo("pin-required");
            });
    }

    @Test
    @DisplayName("User with PIN throws 401 when PIN is incorrect")
    void validate_IncorrectPin_ThrowsUnauthorized() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPin));
        when(passwordEncoder.matches("123456", "$2a$10$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(userId.toString(), "123456"))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(appEx.getErrorCode()).isEqualTo("pin-invalid");
            });
    }

    @Test
    @DisplayName("User with PIN passes when PIN is correct")
    void validate_CorrectPin_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPin));
        when(passwordEncoder.matches("654321", "$2a$10$hashedpin")).thenReturn(true);

        assertThatCode(() -> validator.validate(userId.toString(), "654321"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5 consecutive failed attempts lock PIN with 429 Too Many Requests")
    void validate_FiveFailures_LocksPin() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPin));
        when(passwordEncoder.matches("000000", "$2a$10$hashedpin")).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> validator.validate(userId.toString(), "000000"))
                .isInstanceOf(AppException.class);
        }

        assertThat(pinRateLimiter.isBlocked(userId.toString())).isTrue();

        assertThatThrownBy(() -> validator.validate(userId.toString(), "000000"))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(appEx.getErrorCode()).isEqualTo("pin-locked");
            });
    }
}
