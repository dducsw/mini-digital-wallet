package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.RecipientPreview;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.security.CurrentUser;
import com.minidigitalwallet.security.OwnershipGuard;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        // P0 BOLA fix: ignore client owner_id, wallet always belongs to caller.
        UserPrincipal principal = CurrentUser.getOrNull();
        CreateWalletRequest effective = request;
        if (principal != null) {
            effective = new CreateWalletRequest(
                principal.getId().toString(), request.currency(), request.walletType());
        }
        WalletResponse response = walletService.createWallet(effective);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable("id") UUID id) {
        WalletResponse response = walletService.getWallet(id);
        OwnershipGuard.requireOwner(response.ownerId(), CurrentUser.getOrNull());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<WalletResponse> getWalletBalance(@PathVariable("id") UUID id) {
        WalletResponse response = walletService.getWalletBalance(id);
        OwnershipGuard.requireOwner(response.ownerId(), CurrentUser.getOrNull());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<RecipientPreview> getRecipientPreview(
        @PathVariable("accountNumber") String accountNumber
    ) {
        // P2P preview: any authenticated user can resolve a 12-digit number to send money.
        // No ownership check here by design (account number is shareable like a bank account),
        // so the response is a minimal projection without balances or owner ids.
        return ResponseEntity.ok(walletService.getRecipientPreview(accountNumber));
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> getWalletsByOwner(
        @RequestParam(name = "owner_id", required = false) String ownerId,
        @RequestParam(name = "user_id", required = false) String userId
    ) {
        UserPrincipal principal = CurrentUser.getOrNull();
        String targetId = firstNonBlank(ownerId, userId);
        if (targetId == null && principal != null) {
            targetId = principal.getId().toString();
        }
        if (targetId == null) {
            return ResponseEntity.badRequest().build();
        }
        // P0 BOLA fix: user can only list their own wallets.
        OwnershipGuard.requireSelf(targetId, principal);
        return ResponseEntity.ok(walletService.getWalletsByUserId(targetId));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WalletResponse> updateWalletStatus(
        @PathVariable("id") UUID id,
        @Valid @RequestBody UpdateWalletStatusRequest request
    ) {
        // Check ownership before mutating status.
        OwnershipGuard.requireOwner(walletService.getWalletOwnerId(id), CurrentUser.getOrNull());
        WalletResponse response = walletService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
