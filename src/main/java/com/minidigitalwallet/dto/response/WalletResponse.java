package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalletResponse(
    @JsonProperty("id")
    UUID id,

    @JsonProperty("owner_id")
    String ownerId,

    @JsonProperty("account_type")
    WalletType accountType,

    @JsonProperty("wallet_type")
    WalletType walletType,

    @JsonProperty("account_status")
    WalletStatus accountStatus,

    @JsonProperty("status")
    WalletStatus status,

    @JsonProperty("available_balance")
    Long availableBalance,

    @JsonProperty("ledger_balance")
    Long ledgerBalance,

    @JsonProperty("currency")
    String currency,

    @JsonProperty("version")
    Long version,

    @JsonProperty("created_at")
    Instant createdAt,

    @JsonProperty("updated_at")
    Instant updatedAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
            wallet.getId(),
            wallet.getUserId(),
            wallet.getWalletType(),
            wallet.getWalletType(),
            wallet.getStatus(),
            wallet.getStatus(),
            wallet.getAvailableBalance(),
            wallet.getLedgerBalance(),
            wallet.getCurrency(),
            wallet.getVersion(),
            wallet.getCreatedAt(),
            wallet.getUpdatedAt()
        );
    }
}
