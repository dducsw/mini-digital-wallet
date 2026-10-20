package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.LedgerGuard;
import com.minidigitalwallet.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.minidigitalwallet.dto.response.LedgerTransactionResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final WalletRepository walletRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final List<LedgerGuard> guards;

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
        return doTransfer(referenceId, sourceWalletId, destinationWalletId, amount, transactionType, description, null);
    }

    /**
     * Single-write core: both normal transfers and reversals funnel here so a
     * REVERSAL is persisted once with reversalOf preset (no self-invocation,
     * no second UPDATE after INSERT).
     */
    private LedgerTransaction doTransfer(
        String referenceId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        Long amount,
        TransactionType transactionType,
        String description,
        UUID reversalOfTransactionId
    ) {
        validateTransferRequest(referenceId, sourceWalletId, destinationWalletId, amount);

        Wallets locked = lockBothWallets(sourceWalletId, destinationWalletId);
        validateSameCurrency(locked.source(), locked.destination());
        applyGuards(locked.source(), transactionType, amount);

        com.minidigitalwallet.domain.model.Money transferMoney = com.minidigitalwallet.domain.model.Money.of(amount, locked.source().getCurrency());
        locked.source().debit(transferMoney);
        locked.destination().credit(transferMoney);
        walletRepository.save(locked.source());
        walletRepository.save(locked.destination());

        LedgerTransaction tx = buildBalancedTransaction(
            referenceId, locked.source(), locked.destination(), amount, transactionType, description);
        tx.setReversalOfTransactionId(reversalOfTransactionId);

        try {
            LedgerTransaction savedTx = transactionRepository.save(tx);
            log.info("Ledger transaction executed: ref={}, type={}, amount={}, src={}, dst={}",
                referenceId, transactionType, amount, sourceWalletId, destinationWalletId);
            return savedTx;
        } catch (DataIntegrityViolationException e) {
            // Fast-fail exists-check above can still lose a race between two threads;
            // the DB UNIQUE(reference_id) is the final arbiter -> 409, not 500.
            throw new DuplicateRequestException("Transaction reference already exists: " + referenceId);
        }
    }

    private void validateTransferRequest(String referenceId, UUID sourceWalletId, UUID destinationWalletId, Long amount) {
        if (amount == null || amount <= 0) {
            throw new AppException("Transfer amount must be positive", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        if (Objects.equals(sourceWalletId, destinationWalletId)) {
            throw new AppException("Source and destination wallets must be different", HttpStatus.BAD_REQUEST, "self-transfer-not-allowed");
        }
        if (transactionRepository.existsByReferenceId(referenceId)) {
            throw new DuplicateRequestException("Transaction reference already exists: " + referenceId);
        }
    }

    /** Locks both wallets in deterministic min -> max UUID order to break deadlock cycles. */
    private Wallets lockBothWallets(UUID sourceWalletId, UUID destinationWalletId) {
        UUID firstId = sourceWalletId.compareTo(destinationWalletId) < 0 ? sourceWalletId : destinationWalletId;
        UUID secondId = firstId.equals(sourceWalletId) ? destinationWalletId : sourceWalletId;

        Wallet first = walletRepository.findByIdForUpdate(firstId)
            .orElseThrow(() -> new WalletNotFoundException(firstId));
        Wallet second = walletRepository.findByIdForUpdate(secondId)
            .orElseThrow(() -> new WalletNotFoundException(secondId));

        return firstId.equals(sourceWalletId)
            ? new Wallets(first, second)
            : new Wallets(second, first);
    }

    // Trạng thái FROZEN/CLOSED do WalletState ép khi debit/credit (single source of truth);
    // ở đây chỉ còn check tiền tệ vì state không biết currency của ví đối ứng.
    private void validateSameCurrency(Wallet source, Wallet destination) {
        if (!Objects.equals(source.getCurrency(), destination.getCurrency())) {
            throw new AppException(
                "Currency mismatch: source " + source.getCurrency() + " vs dest " + destination.getCurrency(),
                HttpStatus.BAD_REQUEST,
                "currency-mismatch"
            );
        }
    }

    /**
     * Business policies that must see a consistent state run here, while the
     * source wallet lock is still held (see LedgerGuard).
     */
    private void applyGuards(Wallet debited, TransactionType type, Long amount) {
        for (LedgerGuard guard : guards) {
            guard.check(debited, type, amount);
        }
    }

    /** Builds the journal header plus its two balanced lines (DEBIT source, CREDIT dest). */
    private LedgerTransaction buildBalancedTransaction(
        String referenceId, Wallet source, Wallet destination,
        Long amount, TransactionType type, String description
    ) {
        LedgerTransaction tx = LedgerTransaction.builder()
            .referenceId(referenceId)
            .transactionType(type)
            .amount(amount)
            .currency(source.getCurrency())
            .description(description)
            .build();

        tx.addEntry(LedgerEntry.builder()
            .walletId(source.getId())
            .entryType(EntryType.DEBIT)
            .amount(amount)
            .lineNo(1)
            .balanceAfter(source.getAvailableBalance())
            .build());
        tx.addEntry(LedgerEntry.builder()
            .walletId(destination.getId())
            .entryType(EntryType.CREDIT)
            .amount(amount)
            .lineNo(2)
            .balanceAfter(destination.getAvailableBalance())
            .build());
        return tx;
    }

    private record Wallets(Wallet source, Wallet destination) {
    }

    private record WalletIds(UUID sourceId, UUID destinationId) {
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransaction reverseTransfer(
        UUID originalTransactionId,
        String reversalReferenceId,
        String description
    ) {
        LedgerTransaction original = transactionRepository.findById(originalTransactionId)
            .orElseThrow(() -> new AppException(
                "Original transaction not found: " + originalTransactionId,
                HttpStatus.NOT_FOUND, "transaction-not-found"));
        validateReversible(original);

        // Mirror the money flow: debit the original destination, credit the original source.
        // Single save inside doTransfer with reversalOf preset (append-only, no UPDATE).
        WalletIds counterparties = extractCounterpartyIds(original);
        try {
            return doTransfer(
                reversalReferenceId,
                counterparties.destinationId(),
                counterparties.sourceId(),
                original.getAmount(),
                TransactionType.REVERSAL,
                description != null ? description : "Reversal of " + original.getReferenceId(),
                originalTransactionId
            );
        } catch (DuplicateRequestException e) {
            throw new DuplicateRequestException(
                "Reversal reference already exists: " + reversalReferenceId);
        }
    }

    private void validateReversible(LedgerTransaction original) {
        if (original.getTransactionType() == TransactionType.REVERSAL) {
            throw new AppException("Cannot reverse a REVERSAL transaction",
                HttpStatus.BAD_REQUEST, "reversal-of-reversal-not-allowed");
        }
        if (transactionRepository.existsByReversalOfTransactionId(original.getId())) {
            throw new DuplicateRequestException(
                "Transaction already reversed: " + original.getId());
        }
    }

    /** Reads back the original DEBIT (source) and CREDIT (destination) wallet IDs. */
    private WalletIds extractCounterpartyIds(LedgerTransaction original) {
        UUID sourceId = null;
        UUID destinationId = null;
        for (LedgerEntry entry : original.getEntries()) {
            if (entry.getEntryType() == EntryType.DEBIT) {
                sourceId = entry.getWalletId();
            } else if (entry.getEntryType() == EntryType.CREDIT) {
                destinationId = entry.getWalletId();
            }
        }
        if (sourceId == null || destinationId == null) {
            throw new AppException("Original transaction has unbalanced entries, cannot reverse",
                HttpStatus.CONFLICT, "unbalanced-original-transaction");
        }
        return new WalletIds(sourceId, destinationId);
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

    @Override
    @Transactional(readOnly = true)
    public java.util.List<LedgerEntry> getEntriesByWallet(UUID walletId) {
        return entryRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<LedgerTransactionResponse.EntryDto> getEntryDtosByWallet(UUID walletId) {
        return entryRepository.findByWalletIdOrderByCreatedAtDesc(walletId).stream()
            .map(LedgerTransactionResponse.EntryDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerTransactionResponse.EntryDto> getStatement(
        UUID walletId,
        EntryType type,
        Instant from,
        Instant to,
        Pageable pageable
    ) {
        Specification<LedgerEntry> spec = buildStatementSpec(walletId, type, from, to);
        return entryRepository.findAll(spec, pageable)
            .map(LedgerTransactionResponse.EntryDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public void exportStatementCsv(
        UUID walletId,
        EntryType type,
        Instant from,
        Instant to,
        Writer writer
    ) throws IOException {
        writer.write("entry_id,transaction_id,wallet_id,entry_type,amount,balance_after,created_at\n");
        int page = 0;
        while (true) {
            Page<LedgerEntry> chunk = entryRepository.findAll(
                buildStatementSpec(walletId, type, from, to),
                org.springframework.data.domain.PageRequest.of(page, 1000, Sort.by(Sort.Direction.DESC, "createdAt")));
            for (LedgerEntry e : chunk.getContent()) {
                UUID txId = e.getTransaction() != null ? e.getTransaction().getId() : null;
                writer.write(String.format("%s,%s,%s,%s,%d,%d,%s\n",
                    e.getId(),
                    txId != null ? txId : "",
                    e.getWalletId(),
                    e.getEntryType(),
                    e.getAmount(),
                    e.getBalanceAfter(),
                    e.getCreatedAt()
                ));
            }
            writer.flush();
            if (!chunk.hasNext()) {
                break;
            }
            page++;
        }
    }

    private Specification<LedgerEntry> buildStatementSpec(UUID walletId, EntryType type, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("walletId"), walletId));
            if (type != null) {
                predicates.add(cb.equal(root.get("entryType"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
