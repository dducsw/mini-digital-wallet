package com.minidigitalwallet.ledger;

import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.LedgerFacade;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Double-entry invariant: every transaction holds exactly 2 balanced lines
 * (DEBIT == CREDIT, line_no 1..2 unique) and reversals restore balances.
 */
@SpringBootTest
@Testcontainers
class DoubleEntryInvariantTest {

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
    private LedgerFacade ledgerFacade;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    private Wallet walletA;
    private Wallet walletB;

    @BeforeEach
    void setUp() {
        entryRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        walletA = walletRepository.save(Wallet.builder()
            .userId("INVARIANT_A")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(100_000L)
            .ledgerBalance(100_000L)
            .status(WalletStatus.ACTIVE)
            .build());

        walletB = walletRepository.save(Wallet.builder()
            .userId("INVARIANT_B")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(100_000L)
            .ledgerBalance(100_000L)
            .status(WalletStatus.ACTIVE)
            .build());
    }

    @Test
    @DisplayName("Every transaction: sum(DEBIT) == sum(CREDIT), 2 lines, line_no unique")
    void everyTransaction_IsBalanced() {
        for (int i = 0; i < 10; i++) {
            ledgerFacade.executeTransferWithRetry(
                "INVARIANT-" + i + "-" + UUID.randomUUID(),
                walletA.getId(), walletB.getId(),
                1000L + i, TransactionType.P2P_TRANSFER, "invariant check");
        }

        var transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(10);

        for (LedgerTransaction tx : transactions) {
            var entries = entryRepository.findByTransactionIdOrderByCreatedAtAsc(tx.getId());
            assertThat(entries).hasSize(2);

            long debit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .mapToLong(e -> e.getAmount()).sum();
            long credit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .mapToLong(e -> e.getAmount()).sum();

            assertThat(debit).isEqualTo(credit);
            assertThat(debit).isEqualTo(tx.getAmount());
            assertThat(entries.stream().map(e -> e.getLineNo()).toList())
                .containsExactlyInAnyOrder(1, 2);
        }

        // Money conservation across both wallets
        Wallet a = walletRepository.findById(walletA.getId()).orElseThrow();
        Wallet b = walletRepository.findById(walletB.getId()).orElseThrow();
        assertThat(a.getAvailableBalance() + b.getAvailableBalance()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("Reversal: mirrored entries restore original balances, double-reversal rejected")
    void reversal_RestoresBalances() {
        LedgerTransaction original = ledgerFacade.executeTransferWithRetry(
            "ORIG-" + UUID.randomUUID(),
            walletA.getId(), walletB.getId(),
            5000L, TransactionType.P2P_TRANSFER, "original");

        LedgerTransaction reversal = ledgerFacade.reverseWithRetry(
            original.getId(), "REV-" + UUID.randomUUID(), "customer refund");

        assertThat(reversal.getTransactionType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getReversalOfTransactionId()).isEqualTo(original.getId());

        Wallet a = walletRepository.findById(walletA.getId()).orElseThrow();
        Wallet b = walletRepository.findById(walletB.getId()).orElseThrow();
        assertThat(a.getAvailableBalance()).isEqualTo(100_000L);
        assertThat(b.getAvailableBalance()).isEqualTo(100_000L);

        // Second reversal of the same original must fail
        assertThatThrownBy(() -> ledgerFacade.reverseWithRetry(
            original.getId(), "REV-DUP-" + UUID.randomUUID(), "double refund"))
            .isInstanceOf(RuntimeException.class);

        // Reversal of a reversal must fail
        assertThatThrownBy(() -> ledgerFacade.reverseWithRetry(
            reversal.getId(), "REV-OF-REV-" + UUID.randomUUID(), "not allowed"))
            .isInstanceOf(RuntimeException.class);
    }
}
