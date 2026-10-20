package com.minidigitalwallet.service;

import java.util.UUID;

/**
 * Refresh-token session lifecycle (DIP): opaque token in, raw token out once.
 * Rotation on every refresh; reuse of a revoked token revokes all sessions.
 */
public interface RefreshTokenService {

    /** Issue a new raw refresh token for the user (stores only its hash). */
    IssuedToken issue(UUID userId);

    /** Rotate: consume raw token, revoke it, return a fresh one. */
    Rotation rotate(String rawToken);

    /** Revoke a single token by its raw value (idempotent). */
    void revoke(String rawToken);

    /** Revoke every session of the user (e.g. password change, theft detected). */
    void revokeAll(UUID userId);

    record IssuedToken(String rawToken, java.time.Instant expiresAt) {
    }

    record Rotation(UUID userId, IssuedToken fresh) {
    }
}
