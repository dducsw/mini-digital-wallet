package com.minidigitalwallet.dto.response;

import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    UserStatus status,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }
}
