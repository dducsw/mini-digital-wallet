package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.LoginRequest;
import com.minidigitalwallet.dto.request.RefreshRequest;
import com.minidigitalwallet.dto.request.RegisterRequest;
import com.minidigitalwallet.dto.response.AuthResponse;
import com.minidigitalwallet.dto.response.UserResponse;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new com.minidigitalwallet.exception.AppException(
                "Authentication required",
                HttpStatus.UNAUTHORIZED,
                "auth-required"
            );
        }
        authService.logoutAll(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pin")
    public ResponseEntity<Void> setPin(
        @Valid @RequestBody com.minidigitalwallet.dto.request.SetPinRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new com.minidigitalwallet.exception.AppException(
                "Authentication required",
                HttpStatus.UNAUTHORIZED,
                "auth-required"
            );
        }
        authService.setPin(principal.getId(), request.pin());
        return ResponseEntity.ok().build();
    }
}
