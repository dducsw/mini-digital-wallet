package com.minidigitalwallet.repository;

import com.minidigitalwallet.dto.projection.WalletBalance;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserIdAndCurrencyAndWalletType(String userId, String currency, WalletType walletType);

    boolean existsByUserIdAndCurrencyAndWalletType(String userId, String currency, WalletType walletType);

    List<Wallet> findByUserId(String userId);

    Optional<Wallet> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Single set-based pass that returns only the wallets whose stored balance
     * drifts from SUM(CREDIT) - SUM(DEBIT) of their ledger entries.
     * Deriving the filter in SQL keeps the nightly audit at a constant number of
     * queries (3, independent of wallet count) and O(mismatches) memory, instead
     * of 2 aggregate queries per wallet.
     */
    @Query("""
        SELECT new com.minidigitalwallet.dto.projection.WalletBalance(
            w.id,
            w.availableBalance,
            COALESCE(SUM(CASE WHEN e.entryType = com.minidigitalwallet.entity.enums.EntryType.CREDIT
                              THEN e.amount ELSE -e.amount END), 0))
          FROM Wallet w
          LEFT JOIN LedgerEntry e ON e.walletId = w.id
         GROUP BY w.id, w.availableBalance
        HAVING w.availableBalance <> COALESCE(SUM(CASE WHEN e.entryType = com.minidigitalwallet.entity.enums.EntryType.CREDIT
                              THEN e.amount ELSE -e.amount END), 0)
        """)
    List<WalletBalance> findDriftedWallets();
}
