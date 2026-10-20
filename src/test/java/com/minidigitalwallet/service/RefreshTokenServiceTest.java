package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.RefreshToken;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.RefreshTokenRepository;
import com.minidigitalwallet.security.JwtProperties;
import com.minidigitalwallet.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private JwtProperties jwtProperties;
    private RefreshTokenService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        jwtProperties.setExpirationMs(900000L);
        jwtProperties.setRefreshExpirationMs(604800000L);
        service = new RefreshTokenServiceImpl(repository, jwtProperties);
    }

    @Test
    @DisplayName("issue stores only hash, returns raw once")
    void issue_StoresHash() {
        RefreshTokenService.IssuedToken issued = service.issue(userId);

        assertThat(issued.rawToken()).hasSize(64);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
        assertThat(captor.getValue().getTokenHash()).doesNotContain(issued.rawToken());
    }

    @Test
    @DisplayName("rotate revokes old and issues fresh token for same owner")
    void rotate_Success() {
        RefreshToken current = RefreshToken.builder()
            .id(UUID.randomUUID()).userId(userId)
            .tokenHash("hash-old")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate("raw-old");

        assertThat(rotation.userId()).isEqualTo(userId);
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(rotation.fresh().rawToken()).isNotEmpty();
        verify(repository, times(2)).save(any());
    }

    @Test
    @DisplayName("reuse of revoked token wipes all user sessions")
    void rotate_ReusedToken_RevokesAll() {
        RefreshToken stolen = RefreshToken.builder()
            .id(UUID.randomUUID()).userId(userId)
            .tokenHash("hash-stolen")
            .expiresAt(Instant.now().plusSeconds(3600))
            .revokedAt(Instant.now().minusSeconds(10))
            .build();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> service.rotate("raw-stolen"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("reused");
        verify(repository).deleteByUserId(userId);
    }

    @Test
    @DisplayName("revoke is idempotent on unknown token")
    void revoke_UnknownToken_NoThrow() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("nope");
        verify(repository, never()).save(any());
    }
}
