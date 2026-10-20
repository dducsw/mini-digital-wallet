package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.TransactionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LedgerTransactionResponse(
    @JsonProperty("id")
    UUID id,

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

    @JsonProperty("reversal_of_transaction_id")
    UUID reversalOfTransactionId,

    @JsonProperty("created_at")
    Instant createdAt,

    @JsonProperty("entries")
    List<EntryDto> entries
) {
    public record EntryDto(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("wallet_id")
        UUID walletId,

        @JsonProperty("entry_type")
        EntryType entryType,

        @JsonProperty("amount")
        Long amount,

        @JsonProperty("line_no")
        Integer lineNo,

        @JsonProperty("balance_after")
        Long balanceAfter,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("transaction_id")
        UUID transactionId,

        @JsonProperty("reference_id")
        String referenceId,

        @JsonProperty("transaction_type")
        TransactionType transactionType,

        @JsonProperty("description")
        String description,

        @JsonProperty("reversal_of_transaction_id")
        UUID reversalOfTransactionId
    ) {
        public static EntryDto from(LedgerEntry entry) {
            LedgerTransaction tx = entry.getTransaction();
            return new EntryDto(
                entry.getId(),
                entry.getWalletId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getLineNo(),
                entry.getBalanceAfter(),
                entry.getCreatedAt(),
                tx != null ? tx.getId() : null,
                tx != null ? tx.getReferenceId() : null,
                tx != null ? tx.getTransactionType() : null,
                tx != null ? tx.getDescription() : null,
                tx != null ? tx.getReversalOfTransactionId() : null
            );
        }
    }

    public static LedgerTransactionResponse from(LedgerTransaction tx) {
        List<EntryDto> entryDtos = tx.getEntries() != null
            ? tx.getEntries().stream().map(EntryDto::from).toList()
            : List.of();

        return new LedgerTransactionResponse(
            tx.getId(),
            tx.getReferenceId(),
            tx.getTransactionType(),
            tx.getAmount(),
            tx.getCurrency(),
            tx.getDescription(),
            tx.getReversalOfTransactionId(),
            tx.getCreatedAt(),
            entryDtos
        );
    }
}
