package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TransferRequest(
    @NotNull(message = "source_wallet_id is required")
    @JsonProperty("source_wallet_id")
    UUID sourceWalletId,

    // Legacy: UUID path, optional now (use account number instead).
    @JsonProperty("destination_wallet_id")
    @JsonAlias({"destinationWalletId"})
    UUID destinationWalletId,

    // New: 12-digit account number the recipient reads out (spaces allowed, stripped server-side).
    @JsonProperty("destination_account_number")
    @JsonAlias({"destinationAccountNumber", "account_number", "recipient"})
    @Pattern(regexp = "^[0-9 ]{12,14}$", message = "destination_account_number must be 12 digits")
    String destinationAccountNumber,

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    @JsonProperty("amount")
    Long amount,

    @JsonProperty("description")
    String description
) {
    // Legacy 4-arg constructor for old callers/tests: (source, dest UUID, amount, desc).
    public TransferRequest(UUID sourceWalletId, UUID destinationWalletId, Long amount, String description) {
        this(sourceWalletId, destinationWalletId, null, amount, description);
    }
}
