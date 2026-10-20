package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    /**
     * Global zero-sum check in one scan: DEBIT adds, CREDIT subtracts.
     * A balanced double-entry ledger always nets to 0.
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.entryType = com.minidigitalwallet.entity.enums.EntryType.DEBIT
                                 THEN e.amount ELSE -e.amount END), 0)
          FROM LedgerEntry e
        """)
    long sumNetAmount();

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.walletId = :walletId AND e.entryType = 'DEBIT' AND e.createdAt >= :since")
    long sumDebitsSince(@Param("walletId") UUID walletId, @Param("since") Instant since);
}
