package com.minidigitalwallet.security;

import com.minidigitalwallet.entity.User;
import com.minidigitalwallet.entity.enums.UserRole;
import com.minidigitalwallet.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String email;
    private final String password;
    private final UserRole role;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public static UserPrincipal create(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .authorities(authoritiesOf(user.getRole()))
                .active(user.getStatus() == UserStatus.ACTIVE)
                .build();
    }

    /** Principal rebuilt from JWT claims — no database round trip on the request path. */
    public static UserPrincipal create(UUID id, String username, UserRole role) {
        return UserPrincipal.builder()
                .id(id)
                .username(username)
                .email(null)
                .password(null)
                .role(role)
                .authorities(authoritiesOf(role))
                .active(true)
                .build();
    }

    private static Collection<? extends GrantedAuthority> authoritiesOf(UserRole role) {
        UserRole effective = role == null ? UserRole.USER : role;
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + effective.name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
