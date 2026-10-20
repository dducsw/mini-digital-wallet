package com.minidigitalwallet.security;

import com.minidigitalwallet.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Simple ownership guard against BOLA/IDOR.
 * Every wallet / card / ledger read must belong to the caller.
 * Fail-closed: null principal = unauthenticated -> 401, never skip.
 * Slice tests must set SecurityContext (see ContactControllerTest).
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    public static String currentUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        return principal.getId().toString();
    }

    public static void requireOwner(String resourceOwnerId, UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        String callerId = principal.getId().toString();
        if (resourceOwnerId == null || !resourceOwnerId.equals(callerId)) {
            throw new AppException("Access denied: resource does not belong to current user",
                HttpStatus.FORBIDDEN, "access-denied");
        }
    }

    public static void requireSelf(String targetUserId, UserPrincipal principal) {
        requireOwner(targetUserId, principal);
    }
}
