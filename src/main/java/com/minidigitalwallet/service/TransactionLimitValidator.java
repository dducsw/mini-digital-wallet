package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Transaction limits (SRP):
 * 1. Single transaction cap - stateless, checked at the API boundary.
 * 2. Daily cumulative debit cap per wallet - checked as a {@link LedgerGuard},
 *    i.e. while the source wallet row lock is held, so two concurrent debits
 *    cannot both read the same stale daily total and slip past the cap.
 */
@Component
@RequiredArgsConstructor
public class TransactionLimitValidator implements LedgerGuard {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${app.limits.max-amount-per-tx:1000000}")
    private long maxAmountPerTx;

    @Value("${app.limits.daily-max-amount:5000000}")
    private long dailyMaxAmount;

    /** Per-transaction cap; independent of any stored state, so no lock is needed. */
    public void validateAmount(long amount) {
        if (amount > maxAmountPerTx) {
            throw new AppException(
                String.format("Transaction amount %d exceeds single transaction limit of %d", amount, maxAmountPerTx),
                HttpStatus.FORBIDDEN,
                "limit-exceeded-single"
            );
        }
    }

    @Override
    public void check(Wallet debitedWallet, TransactionType type, long amount) {
        // Refunds give money back, and SYSTEM/settlement accounts are internal
        // contra accounts: neither is a customer spending out of their wallet.
        if (type == TransactionType.REVERSAL || debitedWallet.getWalletType() != WalletType.USER) {
            return;
        }
        long todayDebits = ledgerEntryRepository.sumDebitsSince(
            debitedWallet.getId(), Instant.now().truncatedTo(ChronoUnit.DAYS));
        if (todayDebits + amount > dailyMaxAmount) {
            throw new AppException(
                String.format("Transaction would exceed daily limit of %d (spent today: %d, requested: %d)",
                    dailyMaxAmount, todayDebits, amount),
                HttpStatus.FORBIDDEN,
                "limit-exceeded-daily"
            );
        }
    }
}
