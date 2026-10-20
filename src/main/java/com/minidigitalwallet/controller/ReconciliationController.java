package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.response.ReconciliationSummary;
import com.minidigitalwallet.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ops endpoints, restricted to ROLE_ADMIN by SecurityConfig.
 * Triggering a full-ledger audit is expensive, so it must not be callable by
 * an ordinary customer account. Responses carry counts only — per-wallet
 * balances stay in server logs.
 */
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/run")
    public ResponseEntity<ReconciliationSummary> runNow() {
        return ResponseEntity.ok(reconciliationService.runOnce());
    }

    @GetMapping("/runs")
    public ResponseEntity<List<ReconciliationSummary>> history() {
        return ResponseEntity.ok(reconciliationService.history());
    }
}
