package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
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
        PaymentMethodResponse response = paymentMethodService.linkCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> getPaymentMethods(
        @RequestParam(name = "owner_id", required = false) String ownerId,
        @RequestParam(name = "user_id", required = false) String userId
    ) {
        String targetOwner = (ownerId != null && !ownerId.isBlank()) ? ownerId : userId;
        if (targetOwner == null || targetOwner.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<PaymentMethodResponse> responses = paymentMethodService.getPaymentMethodsByOwner(targetOwner);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentMethodResponse> getPaymentMethod(@PathVariable("id") UUID id) {
        PaymentMethodResponse response = paymentMethodService.getPaymentMethod(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokePaymentMethod(@PathVariable("id") UUID id) {
        paymentMethodService.revokePaymentMethod(id);
        return ResponseEntity.noContent().build();
    }
}
