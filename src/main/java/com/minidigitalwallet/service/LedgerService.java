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
}
