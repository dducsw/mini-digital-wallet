package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);
}
