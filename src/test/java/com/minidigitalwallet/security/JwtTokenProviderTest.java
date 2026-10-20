package com.minidigitalwallet.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.minidigitalwallet.entity.enums.UserRole;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setExpirationMs(3600000L); // 1 hour
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    @DisplayName("generateToken and parse claims successfully")
    void generateAndValidateToken_Success() {
        UUID userId = UUID.randomUUID();
        String username = "alice_wonderland";

        String token = jwtTokenProvider.generateToken(userId, username, UserRole.ADMIN);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(UserRole.ADMIN, jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("validateToken returns false on malformed token")
    void validateToken_MalformedToken_ReturnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.jwt.token"));
    }
}
