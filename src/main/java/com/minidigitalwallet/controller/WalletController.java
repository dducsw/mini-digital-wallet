package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
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
        WalletResponse response = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable("id") UUID id) {
        WalletResponse response = walletService.getWallet(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<WalletResponse> getWalletBalance(@PathVariable("id") UUID id) {
        WalletResponse response = walletService.getWalletBalance(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> getWalletsByOwner(
        @RequestParam(name = "owner_id", required = false) String ownerId,
        @RequestParam(name = "user_id", required = false) String userId
    ) {
        String targetId = (ownerId != null && !ownerId.isBlank()) ? ownerId : userId;
        if (targetId == null || targetId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<WalletResponse> responses = walletService.getWalletsByUserId(targetId);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WalletResponse> updateWalletStatus(
        @PathVariable("id") UUID id,
        @Valid @RequestBody UpdateWalletStatusRequest request
    ) {
        WalletResponse response = walletService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
