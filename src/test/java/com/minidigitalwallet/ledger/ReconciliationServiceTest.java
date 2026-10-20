package com.minidigitalwallet.ledger;

import com.minidigitalwallet.dto.response.ReconciliationSummary;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.ReconciliationRunRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.LedgerFacade;
import com.minidigitalwallet.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4: nightly audit proves snapshots match journals, and flags drift.
 */
@SpringBootTest
@Testcontainers
class ReconciliationServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private LedgerFacade ledgerFacade;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    private Wallet walletA;
    private Wallet walletB;
    private Wallet sysWallet;

    @BeforeEach
    void setUp() {
        entryRepository.deleteAll();
        transactionRepository.deleteAll();
        runRepository.deleteAll();
        walletRepository.deleteAll();

        // SYSTEM settlement wallet may go negative (backs TOPUP funding below).
        sysWallet = walletRepository.save(Wallet.builder()
            .userId("SYS_EXTERNAL_SETTLEMENT")
            .walletType(WalletType.SYSTEM)
            .currency("USD")
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .build());

        walletA = walletRepository.save(Wallet.builder()
            .userId("RECON_A")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .build());

        walletB = walletRepository.save(Wallet.builder()
            .userId("RECON_B")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .build());
    }

    /** Funds A through a real TOPUP journal so snapshot and entries stay consistent. */
    private void fundA() {
        ledgerFacade.executeTransferWithRetry(
            "RECON-FUND-" + UUID.randomUUID(),
            sysWallet.getId(), walletA.getId(),
            100_000L, TransactionType.TOPUP, "fund A");
    }

    @Test
    @DisplayName("Clean ledger: 0 mismatches, zero-sum, run persisted to history")
    void cleanLedger_ReportsOk() {
        fundA();
        ledgerFacade.executeTransferWithRetry(
            "RECON-" + UUID.randomUUID(),
            walletA.getId(), walletB.getId(),
            5000L, TransactionType.P2P_TRANSFER, "recon check");

        ReconciliationSummary summary = reconciliationService.runOnce();

        assertThat(summary.mismatchedCount()).isZero();
        assertThat(summary.globallyBalanced()).isTrue();
        assertThat(summary.status()).isEqualTo("OK");
        assertThat(summary.walletsChecked()).isEqualTo(3);
        assertThat(reconciliationService.history()).hasSize(1);
    }

    @Test
    @DisplayName("Corrupted snapshot: flags the drifted wallet, keeps zero-sum true")
    void corruptedSnapshot_FlagsMismatch() {
        fundA();
        ledgerFacade.executeTransferWithRetry(
            "RECON-" + UUID.randomUUID(),
            walletA.getId(), walletB.getId(),
            5000L, TransactionType.P2P_TRANSFER, "recon check");

        // Simulate drift: snapshot edited outside the ledger (must never happen via API).
        Wallet drifted = walletRepository.findById(walletA.getId()).orElseThrow();
        drifted.setAvailableBalance(drifted.getAvailableBalance() + 999L);
        walletRepository.save(drifted);

        ReconciliationSummary summary = reconciliationService.runOnce();

        assertThat(summary.mismatchedCount()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("MISMATCH");
        assertThat(summary.globallyBalanced()).isTrue();
    }
}
