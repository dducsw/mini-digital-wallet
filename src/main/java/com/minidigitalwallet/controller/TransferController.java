package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.ReversalRequest;
import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.service.IdempotencyService;
import com.minidigitalwallet.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/topup")
    public ResponseEntity<TransferResponse> topUp(
        @Valid @RequestBody TopUpRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String actorId = resolveActorId(request.walletId().toString());
        return idempotencyService.execute(
            actorId,
            "TOPUP",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(request, idempotencyKey))
        );
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransferResponse> withdraw(
        @Valid @RequestBody WithdrawRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(name = "X-Transaction-Pin", required = false) String pin
    ) {
        String actorId = resolveActorId(request.walletId().toString());
        return idempotencyService.execute(
            actorId,
            "WITHDRAW",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(
                pin != null
                    ? transferService.withdraw(request, idempotencyKey, pin)
                    : transferService.withdraw(request, idempotencyKey)
            )
        );
    }

    @PostMapping("/p2p")
    public ResponseEntity<TransferResponse> transfer(
        @Valid @RequestBody TransferRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(name = "X-Transaction-Pin", required = false) String pin
    ) {
        String actorId = resolveActorId(request.sourceWalletId().toString());
        return idempotencyService.execute(
            actorId,
            "P2P_TRANSFER",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(
                pin != null
                    ? transferService.transfer(request, idempotencyKey, pin)
                    : transferService.transfer(request, idempotencyKey)
            )
        );
    }

    @PostMapping("/reversals")
    public ResponseEntity<TransferResponse> reverse(
        @Valid @RequestBody ReversalRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        // Fallback scope theo giao dịch gốc khi request ẩn danh (đã login thì dùng username).
        String actorId = resolveActorId("reversal:" + request.originalTransactionId());
        return idempotencyService.execute(
            actorId,
            "REVERSAL",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(
                transferService.reverse(request.originalTransactionId(), idempotencyKey, request.description())
            )
        );
    }

    private String resolveActorId(String fallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return fallback;
    }
}
