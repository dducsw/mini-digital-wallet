package com.minidigitalwallet.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Hex-encoded secret (>= 64 hex chars = 256 bit). No hardcoded default:
     * set APP_JWT_SECRET env (prod) or app.jwt.secret in test properties.
     * Startup fails fast with a clear message when missing/weak.
     */
    private String secret;

    private long expirationMs = 900000L; // 15 minutes (short-lived access token)

    /** Opaque refresh-token lifetime (rotation on every refresh). */
    private long refreshExpirationMs = 604800000L; // 7 days

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "Missing JWT secret: set APP_JWT_SECRET env (64+ hex chars). "
                + "Generate: openssl rand -hex 32");
        }
        if (secret.trim().length() < 64) {
            throw new IllegalStateException(
                "Weak JWT secret: need 64+ hex chars (256 bit), got " + secret.trim().length());
        }
    }
}
