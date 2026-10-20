package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.entity.RefreshToken;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.RefreshTokenRepository;
import com.minidigitalwallet.security.JwtProperties;
import com.minidigitalwallet.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public IssuedToken issue(UUID userId) {
        String raw = generateRaw();
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs());
        repository.save(RefreshToken.builder()
            .userId(userId)
            .tokenHash(sha256(raw))
            .expiresAt(expiresAt)
            .build());
        return new IssuedToken(raw, expiresAt);
    }

    @Override
    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken current = lookup(rawToken);
        Instant now = Instant.now();
        if (current.isRevoked()) {
            // Reuse of an already-rotated token = possible theft -> kill all sessions.
            repository.deleteByUserId(current.getUserId());
            throw new AppException("Refresh token reused", HttpStatus.UNAUTHORIZED, "refresh-reused");
        }
        if (current.isExpired(now)) {
            repository.delete(current);
            throw new AppException("Refresh token expired", HttpStatus.UNAUTHORIZED, "refresh-expired");
        }
        UUID owner = current.getUserId();
        current.setRevokedAt(now);
        repository.save(current);
        return new Rotation(owner, issue(owner));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256(rawToken.trim())).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevokedAt(Instant.now());
                repository.save(t);
            }
        });
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId) {
        repository.deleteByUserId(userId);
    }

    private RefreshToken lookup(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException("Refresh token is required", HttpStatus.UNAUTHORIZED, "refresh-required");
        }
        return repository.findByTokenHash(sha256(rawToken.trim()))
            .orElseThrow(() -> new AppException("Invalid refresh token",
                HttpStatus.UNAUTHORIZED, "refresh-invalid"));
    }

    private static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
