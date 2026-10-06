package com.minidigitalwallet.concurrency;

import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.InsufficientFundsException;
import com.minidigitalwallet.repository.LedgerEntryRepository;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.LedgerFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LedgerConcurrencyTest {

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
            .userId("USER_CONCURRENT_A")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(1_000_000L) // ,000.00
            .ledgerBalance(1_000_000L)
            .status(WalletStatus.ACTIVE)
            .build());

        walletB = walletRepository.save(Wallet.builder()
            .userId("USER_CONCURRENT_B")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(1_000_000L) // ,000.00
            .ledgerBalance(1_000_000L)
            .status(WalletStatus.ACTIVE)
            .build());
    }

    @Test
    @DisplayName("Bi-directional concurrent transfers: Deterministic lock ordering prevents deadlocks and preserves total balance")
    void concurrentTransfers_BidirectionalNoDeadlock() throws InterruptedException {
        int threadCount = 40;
        int transfersPerDirection = threadCount / 2;
        long transferAmount = 1000L; // .00 each

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Half A -> B, Half B -> A concurrently
        for (int i = 0; i < transfersPerDirection; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerFacade.executeTransferWithRetry(
                        "CONCURRENT-A2B-" + index + "-" + UUID.randomUUID(),
                        walletA.getId(),
                        walletB.getId(),
                        transferAmount,
                        TransactionType.P2P_TRANSFER,
                        "Concurrent A to B"
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerFacade.executeTransferWithRetry(
                        "CONCURRENT-B2A-" + index + "-" + UUID.randomUUID(),
                        walletB.getId(),
                        walletA.getId(),
                        transferAmount,
                        TransactionType.P2P_TRANSFER,
                        "Concurrent B to A"
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All concurrent transfers must complete within 30s without deadlocks").isTrue();
        assertThat(failureCount.get()).as("Zero deadlocks or failures expected under ordered locking").isZero();
        assertThat(successCount.get()).isEqualTo(threadCount);

        Wallet updatedA = walletRepository.findById(walletA.getId()).orElseThrow();
        Wallet updatedB = walletRepository.findById(walletB.getId()).orElseThrow();

        // Total money conserved invariant: 1,000,000 + 1,000,000 = 2,000,000 cents
        long totalBalance = updatedA.getAvailableBalance() + updatedB.getAvailableBalance();
        assertThat(totalBalance).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("Concurrent overdraft protection: Never allows wallet balance to drop below zero")
    void concurrentTransfers_OverdraftPrevention() throws InterruptedException {
        // Create wallet with only 5,000 cents (.00)
        Wallet smallWallet = walletRepository.save(Wallet.builder()
            .userId("USER_SMALL")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(5000L)
            .ledgerBalance(5000L)
            .status(WalletStatus.ACTIVE)
            .build());

        int threadCount = 10;
        long attemptAmount = 2000L; // .00 each -> Total requested = .00 (Only .00 available)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerFacade.executeTransferWithRetry(
                        "OVERDRAFT-TEST-" + index + "-" + UUID.randomUUID(),
                        smallWallet.getId(),
                        walletB.getId(),
                        attemptAmount,
                        TransactionType.P2P_TRANSFER,
                        "Overdraft attempt"
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 2 transfers can succeed (2 * 2000 = 4000 <= 5000)
        // 8 transfers must be rejected
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(insufficientCount.get()).isEqualTo(8);

        Wallet updatedSmall = walletRepository.findById(smallWallet.getId()).orElseThrow();
        assertThat(updatedSmall.getAvailableBalance()).isEqualTo(1000L); // 5000 - 4000 = 1000 cents
        assertThat(updatedSmall.getAvailableBalance()).isGreaterThanOrEqualTo(0L);
    }
}
