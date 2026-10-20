package com.minidigitalwallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit row for one reconciliation run (counts only, never balances).
 */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "wallets_checked", nullable = false)
    @Builder.Default
    private long walletsChecked = 0;

    @Column(name = "mismatched_count", nullable = false)
    @Builder.Default
    private long mismatchedCount = 0;

    @Column(name = "globally_balanced", nullable = false)
    @Builder.Default
    private boolean globallyBalanced = true;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OK";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }
}
