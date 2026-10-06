package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.enums.WalletType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateWalletRequest(
    @NotBlank(message = "owner_id is required")
    @JsonProperty("owner_id")
    @JsonAlias({"user_id", "ownerId", "userId"})
    String ownerId,

    @JsonProperty("currency")
    @Pattern(regexp = "^(USD)?$", message = "Only USD currency is supported")
    String currency,

    @JsonProperty("wallet_type")
    @JsonAlias({"walletType", "account_type", "accountType"})
    WalletType walletType
) {
    public String currency() {
        return (currency == null || currency.isBlank()) ? "USD" : currency;
    }

    public WalletType walletType() {
        return (walletType == null) ? WalletType.USER : walletType;
    }
}
