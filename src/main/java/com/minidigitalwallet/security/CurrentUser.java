package com.minidigitalwallet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads caller from SecurityContext (no method-arg resolver needed,
 * so @WebMvcTest slice tests without security still pass).
 * Null = unauthenticated/test -> ownership checks are skipped.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UserPrincipal getOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal up) {
                return up;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String idOrNull(UserPrincipal ignored) {
        UserPrincipal up = getOrNull();
        return up == null ? null : up.getId().toString();
    }
}
