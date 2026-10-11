package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReversalRequest(
    @NotNull(message = "original_transaction_id is required")
    @JsonProperty("original_transaction_id")
    UUID originalTransactionId,

    @JsonProperty("description")
    String description
) {
}
