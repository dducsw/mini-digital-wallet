package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
import com.minidigitalwallet.security.CurrentUser;
import com.minidigitalwallet.security.OwnershipGuard;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> linkCard(@Valid @RequestBody LinkCardRequest request) {
        // P0 BOLA fix: card always belongs to caller, ignore client owner_id.
        UserPrincipal principal = CurrentUser.getOrNull();
        LinkCardRequest effective = request;
        if (principal != null) {
            effective = new LinkCardRequest(
                principal.getId().toString(),
                request.methodType(), request.provider(),
                request.cardNumber(), request.holderName(), request.isDefault());
        }
        PaymentMethodResponse response = paymentMethodService.linkCard(effective);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> getPaymentMethods(
        @RequestParam(name = "owner_id", required = false) String ownerId,
        @RequestParam(name = "user_id", required = false) String userId
    ) {
        UserPrincipal principal = CurrentUser.getOrNull();
        String targetOwner = (ownerId != null && !ownerId.isBlank()) ? ownerId : userId;
        if (targetOwner == null && principal != null) {
            targetOwner = principal.getId().toString();
        }
        if (targetOwner == null || targetOwner.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        OwnershipGuard.requireSelf(targetOwner, principal);
        List<PaymentMethodResponse> responses = paymentMethodService.getPaymentMethodsByOwner(targetOwner);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentMethodResponse> getPaymentMethod(@PathVariable("id") UUID id) {
        PaymentMethodResponse response = paymentMethodService.getPaymentMethod(id);
        OwnershipGuard.requireOwner(response.ownerId(), CurrentUser.getOrNull());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokePaymentMethod(@PathVariable("id") UUID id) {
        OwnershipGuard.requireOwner(paymentMethodService.getPaymentMethod(id).ownerId(), CurrentUser.getOrNull());
        paymentMethodService.revokePaymentMethod(id);
        return ResponseEntity.noContent().build();
    }
}
