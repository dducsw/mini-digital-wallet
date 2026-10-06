package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.enums.TransactionType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferResponse(
    @JsonProperty("transaction_id")
    UUID transactionId,

    @JsonProperty("reference_id")
    String referenceId,

    @JsonProperty("transaction_type")
    TransactionType transactionType,

    @JsonProperty("amount")
    Long amount,

    @JsonProperty("currency")
    String currency,

    @JsonProperty("description")
    String description,

    @JsonProperty("created_at")
    Instant createdAt
) {
    public static TransferResponse from(LedgerTransaction tx) {
        return new TransferResponse(
            tx.getId(),
            tx.getReferenceId(),
            tx.getTransactionType(),
            tx.getAmount(),
            tx.getCurrency(),
            tx.getDescription(),
            tx.getCreatedAt()
        );
    }
}
