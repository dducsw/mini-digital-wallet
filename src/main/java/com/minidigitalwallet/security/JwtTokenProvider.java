package com.minidigitalwallet.security;

import com.minidigitalwallet.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        // Secret is validated hex at startup (JwtProperties); decode to raw bytes (256+ bit).
        final byte[] keyBytes;
        try {
            keyBytes = java.util.HexFormat.of().parseHex(jwtProperties.getSecret().trim());
        } catch (Exception e) {
            throw new IllegalStateException(
                "APP_JWT_SECRET must be hex-encoded (generate: openssl rand -hex 32)", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "Weak JWT secret: need 32+ bytes (64+ hex chars), got " + keyBytes.length);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UUID userId, String username, UserRole role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpirationMs());
        UserRole effectiveRole = role == null ? UserRole.USER : role;

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                // Role travels in the token, so a role change only takes effect on the
                // next login/refresh (at most one access-token lifetime, 15 minutes).
                .claim("role", effectiveRole.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token: {}", ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("username", String.class);
    }

    /** Tokens issued before V11 carry no role claim and are treated as plain USER. */
    public UserRole getRoleFromToken(String token) {
        String raw = getClaimsFromToken(token).get("role", String.class);
        return raw == null ? UserRole.USER : UserRole.valueOf(raw);
    }
}
