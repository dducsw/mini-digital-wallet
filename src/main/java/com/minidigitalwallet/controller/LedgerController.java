package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.response.LedgerTransactionResponse;
import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.service.LedgerService;
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
    private final LedgerEntryRepository entryRepository;

    @GetMapping("/transactions/{id}")
    public ResponseEntity<LedgerTransactionResponse> getTransaction(@PathVariable("id") UUID id) {
        LedgerTransaction tx = ledgerService.getTransaction(id);
        return ResponseEntity.ok(LedgerTransactionResponse.from(tx));
    }

    @GetMapping("/transactions/reference/{referenceId}")
    public ResponseEntity<LedgerTransactionResponse> getTransactionByRef(@PathVariable("referenceId") String referenceId) {
        LedgerTransaction tx = ledgerService.getTransactionByReferenceId(referenceId);
        return ResponseEntity.ok(LedgerTransactionResponse.from(tx));
    }

    @GetMapping("/wallets/{walletId}/entries")
    public ResponseEntity<List<LedgerTransactionResponse.EntryDto>> getWalletEntries(@PathVariable("walletId") UUID walletId) {
        List<LedgerEntry> entries = entryRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
        List<LedgerTransactionResponse.EntryDto> dtos = entries.stream()
            .map(LedgerTransactionResponse.EntryDto::from)
            .toList();
        return ResponseEntity.ok(dtos);
    }
}
