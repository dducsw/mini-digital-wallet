package com.minidigitalwallet.concurrency;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.IdempotencyStatus;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.repository.IdempotencyRecordRepository;
import com.minidigitalwallet.repository.LedgerTransactionRepository;
import com.minidigitalwallet.repository.PaymentMethodRepository;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.IdempotencyService;
import com.minidigitalwallet.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class IdempotencyConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private com.minidigitalwallet.repository.LedgerEntryRepository entryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Wallet userWallet;
    private PaymentMethod paymentCard;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        entryRepository.deleteAll();
        transactionRepository.deleteAll();
        paymentMethodRepository.deleteAll();
        walletRepository.deleteAll();

        // System settlement wallet with exact UUID
        jdbcTemplate.update("""
            INSERT INTO wallets (id, user_id, wallet_type, currency, available_balance, ledger_balance, status, version, created_at, updated_at)
            VALUES ('00000000-0000-0000-0000-000000000001', 'SYS_EXTERNAL_SETTLEMENT', 'SYSTEM', 'USD', 0, 0, 'ACTIVE', 0, NOW(), NOW())
        """);

        // User wallet
        userWallet = Wallet.builder()
            .userId("user-alice")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .version(0L)
            .build();
        userWallet = walletRepository.save(userWallet);

        // User payment card
        paymentCard = PaymentMethod.builder()
            .ownerId("user-alice")
            .methodType(com.minidigitalwallet.entity.enums.MethodType.DEBIT_CARD)
            .provider(com.minidigitalwallet.entity.enums.CardProvider.VISA)
            .maskedNumber("•••• 4242")
            .holderName("ALICE TRAN")
            .paymentToken("tok_mock_visa_4242")
            .isDefault(true)
            .status(PaymentMethodStatus.ACTIVE)
            .build();
        paymentCard = paymentMethodRepository.save(paymentCard);
    }

    @Test
    @DisplayName("Phase 3 DoD: 100 concurrent threads with same idempotency key -> exactly 1 executes, others 409 or replay")
    void concurrentIdempotencyRequests_executeExactlyOnce() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        String actorId = "user-alice";
        String idempotencyKey = "key-" + UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userWallet.getId(), paymentCard.getId(), 5000L, "Topup concurrency test");

        AtomicInteger executedCount = new AtomicInteger(0);
        AtomicInteger replayCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        java.util.List<String> otherErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ResponseEntity<TransferResponse> response = idempotencyService.execute(
                        actorId,
                        "TOPUP",
                        idempotencyKey,
                        request,
                        TransferResponse.class,
                        () -> {
                            executedCount.incrementAndGet();
                            return ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(request, idempotencyKey));
                        }
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        replayCount.incrementAndGet();
                    }
                } catch (AppException e) {
                    if (e.getStatus() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherErrors.add("AppException: " + e.getStatus() + " " + e.getMessage());
                    }
                } catch (Throwable t) {
                    otherErrors.add(t.getClass().getSimpleName() + ": " + t.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(otherErrors).as("No unexpected errors should occur").isEmpty();
        assertThat(finished).isTrue();
        // Exactly ONE thread executed the action
        assertThat(executedCount.get()).isEqualTo(1);
        // All threads either got 409 conflict or got replayed 201 response
        assertThat(conflictCount.get() + replayCount.get()).isEqualTo(threadCount);

        // DB state: Exactly 1 transaction recorded in ledger
        assertThat(transactionRepository.count()).isEqualTo(1);

        // Verification of replay: Client retry after completion gets exact cached response
        ResponseEntity<TransferResponse> retryResponse = idempotencyService.execute(
            actorId,
            "TOPUP",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> {
                executedCount.incrementAndGet();
                return ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(request, idempotencyKey));
            }
        );

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retryResponse.getBody()).isNotNull();
        assertThat(retryResponse.getBody().amount()).isEqualTo(5000L);
        // executedCount did NOT increase on retry
        assertThat(executedCount.get()).isEqualTo(1);
        // DB transaction count is STILL 1
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Payload modification with same idempotency key must throw 422 Unprocessable Entity")
    void payloadMismatch_throws422UnprocessableEntity() {
        String actorId = "user-alice";
        String idempotencyKey = "key-" + UUID.randomUUID();
        TopUpRequest originalRequest = new TopUpRequest(userWallet.getId(), paymentCard.getId(), 5000L, "Original");
        TopUpRequest alteredRequest = new TopUpRequest(userWallet.getId(), paymentCard.getId(), 10000L, "Altered");

        // 1. First execution succeeds
        ResponseEntity<TransferResponse> response1 = idempotencyService.execute(
            actorId,
            "TOPUP",
            idempotencyKey,
            originalRequest,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(originalRequest, idempotencyKey))
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Second execution with modified payload -> throws 422
        assertThatThrownBy(() -> idempotencyService.execute(
            actorId,
            "TOPUP",
            idempotencyKey,
            alteredRequest,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(alteredRequest, idempotencyKey))
        ))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    @DisplayName("Lease recovery: Expired IN_PROGRESS lease is automatically reclaimed")
    void expiredLease_isAutomaticallyReclaimed() throws Exception {
        String actorId = "user-alice";
        String idempotencyKey = "key-" + UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userWallet.getId(), paymentCard.getId(), 5000L, "Lease recovery");

        String json = objectMapper.writeValueAsString(request);
        String hash = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        // Simulate crashed node by inserting an expired IN_PROGRESS record
        var record = com.minidigitalwallet.entity.IdempotencyRecord.builder()
            .actorId(actorId)
            .operation("TOPUP")
            .idempotencyKey(idempotencyKey)
            .requestHash(hash)
            .status(IdempotencyStatus.IN_PROGRESS)
            .leaseExpiresAt(Instant.now().minusSeconds(60)) // Expired!
            .createdAt(Instant.now().minusSeconds(60))
            .updatedAt(Instant.now().minusSeconds(60))
            .build();
        idempotencyRecordRepository.saveAndFlush(record);

        // Now executing request should reclaim the lease and successfully complete
        ResponseEntity<TransferResponse> response = idempotencyService.execute(
            actorId,
            "TOPUP",
            idempotencyKey,
            request,
            TransferResponse.class,
            () -> ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(request, idempotencyKey))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var updated = idempotencyRecordRepository.findByActorIdAndOperationAndIdempotencyKey(actorId, "TOPUP", idempotencyKey);
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }
}
