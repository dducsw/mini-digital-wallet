package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.LoginRequest;
import com.minidigitalwallet.dto.request.RegisterRequest;
import com.minidigitalwallet.dto.response.AuthResponse;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.UserStatus;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.InvalidCredentialsException;
import com.minidigitalwallet.exception.UserAlreadyExistsException;
import com.minidigitalwallet.repository.UserRepository;
import com.minidigitalwallet.security.JwtProperties;
import com.minidigitalwallet.security.JwtTokenProvider;
import com.minidigitalwallet.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private WalletService walletService;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setExpirationMs(86400000L);
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                jwtProperties,
                walletService
        );
    }

    @Test
    @DisplayName("register creates user, default wallet, and issues JWT token")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("bob_smith", "bob@example.com", "Password123!", "Bob Smith", "USD");
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        when(userRepository.existsByUsername("bob_smith")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");

        User savedUser = User.builder()
                .id(userId)
                .username("bob_smith")
                .email("bob@example.com")
                .passwordHash("hashedPassword")
                .fullName("Bob Smith")
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Wallet dummyWallet = Wallet.builder()
                .id(walletId)
                .userId(userId.toString())
                .walletType(WalletType.USER)
                .currency("USD")
                .availableBalance(0L)
                .ledgerBalance(0L)
                .status(WalletStatus.ACTIVE)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(walletService.createWallet(any(CreateWalletRequest.class))).thenReturn(WalletResponse.from(dummyWallet));
        when(jwtTokenProvider.generateToken(userId, "bob_smith")).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(userId, response.userId());
        assertEquals(walletId, response.defaultWalletId());

        verify(userRepository).save(any(User.class));
        verify(walletService).createWallet(any(CreateWalletRequest.class));
    }

    @Test
    @DisplayName("register throws UserAlreadyExistsException if username exists")
    void register_DuplicateUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest("existing_user", "test@example.com", "Password123!", "Test", "USD");
        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login returns token when credentials match")
    void login_Success() {
        LoginRequest request = new LoginRequest("alice", "CorrectPassword!");
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("encodedPassword")
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CorrectPassword!", "encodedPassword")).thenReturn(true);

        Wallet dummyWallet = Wallet.builder()
                .id(walletId)
                .userId(userId.toString())
                .walletType(WalletType.USER)
                .currency("USD")
                .availableBalance(10000L)
                .ledgerBalance(10000L)
                .status(WalletStatus.ACTIVE)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(walletService.getWalletsByUserId(userId.toString())).thenReturn(Collections.singletonList(WalletResponse.from(dummyWallet)));
        when(jwtTokenProvider.generateToken(userId, "alice")).thenReturn("alice-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("alice-jwt-token", response.accessToken());
        assertEquals(userId, response.userId());
        assertEquals(walletId, response.defaultWalletId());
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException on wrong password")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("alice", "WrongPassword!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .passwordHash("encodedPassword")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "encodedPassword")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("login throws InvalidCredentialsException if username not found")
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("unknown", "anyPassword");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}
