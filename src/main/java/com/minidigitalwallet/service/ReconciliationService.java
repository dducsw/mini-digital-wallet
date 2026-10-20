package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.projection.WalletBalance;
import com.minidigitalwallet.dto.response.ReconciliationSummary;
import com.minidigitalwallet.entity.ReconciliationRun;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.repository.ReconciliationRunRepository;
import com.minidigitalwallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Phase 4 nightly audit: proves every wallet snapshot matches its journal
 * (available_balance == SUM(CREDIT) - SUM(DEBIT)) and the whole ledger is
 * zero-sum. Mismatch details go to server logs only; API returns counts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository entryRepository;
    private final ReconciliationRunRepository runRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${app.reconciliation.cron:0 0 2 * * *}")
    public void runScheduled() {
        runOnce();
    }

    @Transactional
    public ReconciliationSummary runOnce() {
        Instant start = Instant.now();

        // 1. Snapshot drift: one set-based query returns only mismatched wallets.
        List<WalletBalance> drifted = walletRepository.findDriftedWallets();
        for (WalletBalance drift : drifted) {
            log.warn("Reconciliation mismatch: wallet={} derived={} stored={} diff={}",
                drift.walletId(), drift.derivedBalance(), drift.storedBalance(),
                drift.storedBalance() - drift.derivedBalance());
        }

        // 2. Global invariant: a balanced double-entry ledger always nets to zero.
        boolean globallyBalanced = entryRepository.sumNetAmount() == 0;
        if (!globallyBalanced) {
            log.warn("Reconciliation: ledger is NOT zero-sum (total DEBIT != total CREDIT)");
        }

        long mismatched = drifted.size();
        String status = (mismatched == 0 && globallyBalanced) ? "OK" : "MISMATCH";
        ReconciliationRun run = runRepository.save(ReconciliationRun.builder()
            .startedAt(start)
            .finishedAt(Instant.now())
            .walletsChecked(walletRepository.count())
            .mismatchedCount(mismatched)
            .globallyBalanced(globallyBalanced)
            .status(status)
            .build());

        meterRegistry.counter("reconciliation.runs").increment();
        if (mismatched > 0) {
            meterRegistry.counter("reconciliation.mismatched.wallets").increment(mismatched);
        }
        log.info("Reconciliation {}: checked={} mismatched={} zero-sum={}",
            status, run.getWalletsChecked(), mismatched, globallyBalanced);
        return ReconciliationSummary.from(run);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationSummary> history() {
        return runRepository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(ReconciliationSummary::from)
            .toList();
    }
}
