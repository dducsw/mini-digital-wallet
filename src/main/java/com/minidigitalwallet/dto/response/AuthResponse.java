package com.minidigitalwallet.dto.response;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    String username,
    String email,
    UUID defaultWalletId,
    String refreshToken,
    long refreshExpiresIn
) {
    public static AuthResponse of(String accessToken, long expiresIn, UserResponse user,
                                  UUID defaultWalletId, String refreshToken, long refreshExpiresIn) {
        return new AuthResponse(
            accessToken,
            "Bearer",
            expiresIn,
            user.id(),
            user.username(),
            user.email(),
            defaultWalletId,
            refreshToken,
            refreshExpiresIn
        );
    }
}
