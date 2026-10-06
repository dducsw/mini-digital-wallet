package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TransferRequest(
    @NotNull(message = "source_wallet_id is required")
    @JsonProperty("source_wallet_id")
    UUID sourceWalletId,

    @NotNull(message = "destination_wallet_id is required")
    @JsonProperty("destination_wallet_id")
    UUID destinationWalletId,

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    @JsonProperty("amount")
    Long amount,

    @JsonProperty("description")
    String description
) {
}
