package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TopUpRequest(
    @NotNull(message = "wallet_id is required")
    @JsonProperty("wallet_id")
    UUID walletId,

    @NotNull(message = "payment_method_id is required")
    @JsonProperty("payment_method_id")
    UUID paymentMethodId,

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    @JsonProperty("amount")
    Long amount,

    @JsonProperty("description")
    String description
) {
}
