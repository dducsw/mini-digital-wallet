package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.enums.TransactionType;

import java.util.UUID;

public interface LedgerService {

    LedgerTransaction executeTransfer(
        String referenceId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        Long amount,
        TransactionType transactionType,
        String description
    );

    /**
     * Append-only reversal: creates a new REVERSAL transaction with mirrored
     * entries (credit back the original source, debit the original destination).
     * Never updates or deletes the original transaction.
     */
    LedgerTransaction reverseTransfer(
        UUID originalTransactionId,
        String reversalReferenceId,
        String description
    );

    LedgerTransaction getTransaction(UUID transactionId);

    LedgerTransaction getTransactionByReferenceId(String referenceId);

    /** Statement lines for one wallet, newest first. Controller checks ownership first. */
    java.util.List<com.minidigitalwallet.entity.LedgerEntry> getEntriesByWallet(UUID walletId);

    /** Same as getEntriesByWallet but mapped to DTOs inside the transaction (safe lazy access). */
    java.util.List<com.minidigitalwallet.dto.response.LedgerTransactionResponse.EntryDto> getEntryDtosByWallet(UUID walletId);

    org.springframework.data.domain.Page<com.minidigitalwallet.dto.response.LedgerTransactionResponse.EntryDto> getStatement(
        UUID walletId,
        com.minidigitalwallet.entity.enums.EntryType type,
        java.time.Instant from,
        java.time.Instant to,
        org.springframework.data.domain.Pageable pageable
    );

    void exportStatementCsv(
        UUID walletId,
        com.minidigitalwallet.entity.enums.EntryType type,
        java.time.Instant from,
        java.time.Instant to,
        java.io.Writer writer
    ) throws java.io.IOException;
}
