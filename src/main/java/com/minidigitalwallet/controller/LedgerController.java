package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.response.LedgerTransactionResponse;
import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.security.CurrentUser;
import com.minidigitalwallet.security.OwnershipGuard;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.LedgerService;
import com.minidigitalwallet.service.WalletLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final WalletLookup wallets;

    @GetMapping("/transactions/{id}")
    public ResponseEntity<LedgerTransactionResponse> getTransaction(@PathVariable("id") UUID id) {
        LedgerTransaction tx = ledgerService.getTransaction(id);
        requireVisible(tx, CurrentUser.getOrNull());
        return ResponseEntity.ok(LedgerTransactionResponse.from(tx));
    }

    @GetMapping("/transactions/reference/{referenceId}")
    public ResponseEntity<LedgerTransactionResponse> getTransactionByRef(@PathVariable("referenceId") String referenceId) {
        LedgerTransaction tx = ledgerService.getTransactionByReferenceId(referenceId);
        requireVisible(tx, CurrentUser.getOrNull());
        return ResponseEntity.ok(LedgerTransactionResponse.from(tx));
    }

    /** Deprecated: unbounded list. Prefer GET /statement with pagination. Kept for compatibility. */
    @GetMapping("/wallets/{walletId}/entries")
    public ResponseEntity<List<LedgerTransactionResponse.EntryDto>> getWalletEntries(@PathVariable("walletId") UUID walletId) {
        OwnershipGuard.requireOwner(wallets.getWalletOwnerId(walletId), CurrentUser.getOrNull());
        return ResponseEntity.ok(ledgerService.getEntryDtosByWallet(walletId));
    }

    @GetMapping("/wallets/{walletId}/statement")
    public ResponseEntity<org.springframework.data.domain.Page<LedgerTransactionResponse.EntryDto>> getStatement(
        @PathVariable("walletId") UUID walletId,
        @RequestParam(name = "type", required = false) com.minidigitalwallet.entity.enums.EntryType type,
        @RequestParam(name = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
        @RequestParam(name = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
        @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        OwnershipGuard.requireOwner(wallets.getWalletOwnerId(walletId), CurrentUser.getOrNull());
        return ResponseEntity.ok(ledgerService.getStatement(walletId, type, from, to, pageable));
    }

    @GetMapping(value = "/wallets/{walletId}/statement/export", produces = "text/csv")
    public void exportStatementCsv(
        @PathVariable("walletId") UUID walletId,
        @RequestParam(name = "type", required = false) com.minidigitalwallet.entity.enums.EntryType type,
        @RequestParam(name = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
        @RequestParam(name = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
        jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        OwnershipGuard.requireOwner(wallets.getWalletOwnerId(walletId), CurrentUser.getOrNull());
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"statement-" + walletId + ".csv\"");
        ledgerService.exportStatementCsv(walletId, type, from, to, response.getWriter());
    }

    /** Caller must own at least one wallet in the transaction (sender or receiver). Fail-closed. */
    private void requireVisible(LedgerTransaction tx, UserPrincipal principal) {
        if (principal == null) {
            throw new com.minidigitalwallet.exception.AppException("Authentication required",
                org.springframework.http.HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        if (tx.getEntries() == null || tx.getEntries().isEmpty()) {
            throw new com.minidigitalwallet.exception.AppException(
                "Access denied: transaction does not involve current user",
                org.springframework.http.HttpStatus.FORBIDDEN, "access-denied");
        }
        String callerId = principal.getId().toString();
        for (LedgerEntry e : tx.getEntries()) {            try {
                if (callerId.equals(wallets.getWalletOwnerId(e.getWalletId()))) {
                    return;
                }
            } catch (Exception ignored) {
                // System wallets (SYS_*) have no user owner -> skip.
            }
        }
        throw new com.minidigitalwallet.exception.AppException(
            "Access denied: transaction does not involve current user",
            org.springframework.http.HttpStatus.FORBIDDEN, "access-denied");
    }
}
