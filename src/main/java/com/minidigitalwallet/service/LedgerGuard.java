package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.TransactionType;

/**
 * Policy hook executed inside the ledger transaction, right after both wallet
 * rows are locked and before any balance is touched.
 *
 * It exists so business limits that depend on an aggregate (a daily debit cap)
 * observe a state that cannot change concurrently: while the source wallet row
 * is locked, every other debit from that wallet waits, so a check performed here
 * cannot be raced by a second request reading the same stale total.
 *
 * Implementations must be side-effect free and throw on rejection.
 */
public interface LedgerGuard {

    /**
     * @param debitedWallet the wallet being debited (not the credited one)
     * @param type          the transaction type about to be booked
     * @param amount        amount in minor units
     */
    void check(Wallet debitedWallet, TransactionType type, long amount);
}
