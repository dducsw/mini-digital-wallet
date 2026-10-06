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

    LedgerTransaction getTransaction(UUID transactionId);

    LedgerTransaction getTransactionByReferenceId(String referenceId);
}
