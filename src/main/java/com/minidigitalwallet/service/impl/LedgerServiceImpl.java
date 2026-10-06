package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.WalletFrozenException;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final WalletRepository walletRepository;
    private final LedgerTransactionRepository transactionRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransaction executeTransfer(
        String referenceId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        Long amount,
        TransactionType transactionType,
        String description
    ) {
        if (amount == null || amount <= 0) {
            throw new AppException("Transfer amount must be positive", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        if (Objects.equals(sourceWalletId, destinationWalletId)) {
            throw new AppException("Source and destination wallets must be different", HttpStatus.BAD_REQUEST, "self-transfer-not-allowed");
        }
        if (transactionRepository.existsByReferenceId(referenceId)) {
            throw new DuplicateRequestException("Transaction reference already exists: " + referenceId);
        }

        // Deterministic Lock Ordering: sort UUIDs lexicographically to eliminate circular wait deadlocks
        UUID firstLockId = sourceWalletId.compareTo(destinationWalletId) < 0 ? sourceWalletId : destinationWalletId;
        UUID secondLockId = sourceWalletId.compareTo(destinationWalletId) < 0 ? destinationWalletId : sourceWalletId;

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstLockId)
            .orElseThrow(() -> new WalletNotFoundException(firstLockId));
        Wallet secondWallet = walletRepository.findByIdForUpdate(secondLockId)
            .orElseThrow(() -> new WalletNotFoundException(secondLockId));

        Wallet sourceWallet = firstWallet.getId().equals(sourceWalletId) ? firstWallet : secondWallet;
        Wallet destWallet = firstWallet.getId().equals(destinationWalletId) ? firstWallet : secondWallet;

        // Verify status
        if (sourceWallet.isFrozen()) {
            throw new WalletFrozenException(sourceWallet.getId());
        }
        if (sourceWallet.isClosed()) {
            throw new AppException("Source wallet is CLOSED", HttpStatus.BAD_REQUEST, "wallet-closed");
        }
        if (destWallet.isFrozen()) {
            throw new AppException("Destination wallet is FROZEN", HttpStatus.BAD_REQUEST, "wallet-frozen");
        }
        if (destWallet.isClosed()) {
            throw new AppException("Destination wallet is CLOSED", HttpStatus.BAD_REQUEST, "wallet-closed");
        }

        // Currency verification
        if (!Objects.equals(sourceWallet.getCurrency(), destWallet.getCurrency())) {
            throw new AppException(
                "Currency mismatch: source " + sourceWallet.getCurrency() + " vs dest " + destWallet.getCurrency(),
                HttpStatus.BAD_REQUEST,
                "currency-mismatch"
            );
        }

        // Update balances (debit validates sufficient funds for USER wallets)
        sourceWallet.debit(amount);
        destWallet.credit(amount);

        walletRepository.save(sourceWallet);
        walletRepository.save(destWallet);

        // Build Journal Header
        LedgerTransaction tx = LedgerTransaction.builder()
            .referenceId(referenceId)
            .transactionType(transactionType)
            .amount(amount)
            .currency(sourceWallet.getCurrency())
            .description(description)
            .build();

        // Build Journal Lines (Double-Entry Bookkeeping)
        LedgerEntry debitEntry = LedgerEntry.builder()
            .walletId(sourceWallet.getId())
            .entryType(EntryType.DEBIT)
            .amount(amount)
            .balanceAfter(sourceWallet.getAvailableBalance())
            .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
            .walletId(destWallet.getId())
            .entryType(EntryType.CREDIT)
            .amount(amount)
            .balanceAfter(destWallet.getAvailableBalance())
            .build();

        tx.addEntry(debitEntry);
        tx.addEntry(creditEntry);

        LedgerTransaction savedTx = transactionRepository.save(tx);
        log.info("Ledger transaction executed: ref={}, type={}, amount={}, src={}, dst={}",
            referenceId, transactionType, amount, sourceWalletId, destinationWalletId);

        return savedTx;
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerTransaction getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new AppException("Transaction not found: " + transactionId, HttpStatus.NOT_FOUND, "transaction-not-found"));
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerTransaction getTransactionByReferenceId(String referenceId) {
        return transactionRepository.findByReferenceId(referenceId)
            .orElseThrow(() -> new AppException("Transaction not found for ref: " + referenceId, HttpStatus.NOT_FOUND, "transaction-not-found"));
    }
}
