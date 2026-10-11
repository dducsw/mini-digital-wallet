package com.minidigitalwallet.entity;

import com.minidigitalwallet.entity.enums.EntryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ledger_entries_tx_line", columnNames = {"transaction_id", "line_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "balance_after", nullable = false)    private Long balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
