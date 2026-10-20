package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.LoginRequest;
import com.minidigitalwallet.dto.request.RegisterRequest;
import com.minidigitalwallet.dto.response.AuthResponse;
import com.minidigitalwallet.dto.response.UserResponse;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.entity.enums.UserStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.InvalidCredentialsException;
import com.minidigitalwallet.exception.UserAlreadyExistsException;
import com.minidigitalwallet.exception.UserNotFoundException;
import com.minidigitalwallet.repository.UserRepository;
import com.minidigitalwallet.security.JwtProperties;
import com.minidigitalwallet.security.JwtTokenProvider;
import com.minidigitalwallet.security.LoginRateLimiter;
import com.minidigitalwallet.service.AuthService;
import com.minidigitalwallet.service.RefreshTokenService;
import com.minidigitalwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final WalletService walletService;
    private final LoginRateLimiter rateLimiter;
    private final RefreshTokenService refreshTokens;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email '" + email + "' is already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName() != null ? request.fullName().trim() : null)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-provision initial wallet for the registered user
        String currency = StringUtils.hasText(request.currency()) ? request.currency().toUpperCase().trim() : "USD";
        CreateWalletRequest walletRequest = new CreateWalletRequest(
                savedUser.getId().toString(),
                currency,
                WalletType.USER
        );
        WalletResponse defaultWallet = walletService.createWallet(walletRequest);

        String token = jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getUsername(), savedUser.getRole());
        UserResponse userResponse = UserResponse.from(savedUser);
        RefreshTokenService.IssuedToken refresh = refreshTokens.issue(savedUser.getId());

        log.info("Registered user {} ({}) with default wallet {}", savedUser.getUsername(), savedUser.getId(), defaultWallet.id());
        return AuthResponse.of(token, jwtProperties.getExpirationMs() / 1000, userResponse,
            defaultWallet.id(), refresh.rawToken(), jwtProperties.getRefreshExpirationMs() / 1000);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        if (rateLimiter.isBlocked(username)) {
            throw new AppException("Too many failed login attempts, please try again later",
                HttpStatus.TOO_MANY_REQUESTS, "too-many-requests");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(username);
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(username);
            throw new InvalidCredentialsException();
        }
        rateLimiter.reset(username);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("Account is " + user.getStatus().name().toLowerCase(), HttpStatus.FORBIDDEN, "account-disabled");
        }

        List<WalletResponse> wallets = walletService.getWalletsByUserId(user.getId().toString());
        UUID defaultWalletId = wallets.isEmpty() ? null : wallets.get(0).id();

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        UserResponse userResponse = UserResponse.from(user);
        RefreshTokenService.IssuedToken refresh = refreshTokens.issue(user.getId());

        log.info("User {} ({}) logged in successfully", user.getUsername(), user.getId());
        return AuthResponse.of(token, jwtProperties.getExpirationMs() / 1000, userResponse,
            defaultWalletId, refresh.rawToken(), jwtProperties.getRefreshExpirationMs() / 1000);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        User user = userRepository.findById(rotation.userId())
            .orElseThrow(() -> new UserNotFoundException(rotation.userId()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokens.revokeAll(rotation.userId());
            throw new AppException("Account is " + user.getStatus().name().toLowerCase(),
                HttpStatus.FORBIDDEN, "account-disabled");
        }
        List<WalletResponse> wallets = walletService.getWalletsByUserId(user.getId().toString());
        UUID defaultWalletId = wallets.isEmpty() ? null : wallets.get(0).id();
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("Refreshed session for user {} ({})", user.getUsername(), user.getId());
        return AuthResponse.of(token, jwtProperties.getExpirationMs() / 1000,
            UserResponse.from(user), defaultWalletId,
            rotation.fresh().rawToken(), jwtProperties.getRefreshExpirationMs() / 1000);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokens.revokeAll(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void setPin(UUID userId, String pin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setPinHash(passwordEncoder.encode(pin));
        userRepository.save(user);
        log.info("Transaction PIN updated for user {} ({})", user.getUsername(), userId);
    }
}
