package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.LoginRequest;
import com.minidigitalwallet.dto.request.RegisterRequest;
import com.minidigitalwallet.dto.response.AuthResponse;
import com.minidigitalwallet.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /** Rotate refresh token -> new access + refresh pair (reuse kills all sessions). */
    AuthResponse refresh(String rawRefreshToken);

    /** Revoke one refresh token (idempotent, for logout). */
    void logout(String rawRefreshToken);

    /** Revoke every session of the user (logout all devices). */
    void logoutAll(UUID userId);

    UserResponse getCurrentUser(UUID userId);

    void setPin(UUID userId, String pin);
}
