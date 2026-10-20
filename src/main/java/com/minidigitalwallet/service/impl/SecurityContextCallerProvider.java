package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.CallerProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Production CallerProvider: reads Spring SecurityContext.
 */
@Component
public class SecurityContextCallerProvider implements CallerProvider {

    @Override
    public String currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.getId().toString();
        }
        return null;
    }
}
