package com.minidigitalwallet.entity;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.domain.state.ActiveWalletState;
import com.minidigitalwallet.domain.state.ClosedWalletState;
import com.minidigitalwallet.domain.state.FrozenWalletState;
import com.minidigitalwallet.domain.state.WalletState;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_wallets_user_currency_type", columnNames = {"user_id", "currency", "wallet_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 32)
    @Builder.Default
    private WalletType walletType = WalletType.USER;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "available_balance", nullable = false)
    @Builder.Default
    private Long availableBalance = 0L;

    @Column(name = "ledger_balance", nullable = false)
    @Builder.Default
    private Long ledgerBalance = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.currency == null) {
            this.currency = "USD";
        }
        if (this.walletType == null) {
            this.walletType = WalletType.USER;
        }
        if (this.status == null) {
            this.status = WalletStatus.ACTIVE;
        }
        if (this.availableBalance == null) {
            this.availableBalance = 0L;
        }
        if (this.ledgerBalance == null) {
            this.ledgerBalance = 0L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Money getMoneyBalance() {
        return Money.of(this.availableBalance, this.currency);
    }

    public WalletState getState() {
        if (this.status == null) {
            return ActiveWalletState.INSTANCE;
        }
        return switch (this.status) {
            case ACTIVE -> ActiveWalletState.INSTANCE;
            case FROZEN -> FrozenWalletState.INSTANCE;
            case CLOSED -> ClosedWalletState.INSTANCE;
        };
    }

    public boolean isActive() {
        return this.status == WalletStatus.ACTIVE;
    }

    public boolean isFrozen() {
        return this.status == WalletStatus.FROZEN;
    }

    public boolean isClosed() {
        return this.status == WalletStatus.CLOSED;
    }

    public void debit(Money money) {
        if (money == null) {
            throw new AppException("Debit amount cannot be null", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        money.validateCurrency(this.currency);
        getState().debit(this, money);
    }

    public void debit(Long amount) {
        debit(Money.of(amount, this.currency));
    }

    public void credit(Money money) {
        if (money == null) {
            throw new AppException("Credit amount cannot be null", HttpStatus.BAD_REQUEST, "invalid-amount");
        }
        money.validateCurrency(this.currency);
        getState().credit(this, money);
    }

    public void credit(Long amount) {
        credit(Money.of(amount, this.currency));
    }

    public void freeze() {
        getState().freeze(this);
    }

    public void unfreeze() {
        getState().unfreeze(this);
    }

    public void close() {
        getState().close(this);
    }
}
