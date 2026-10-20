package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.ReconciliationRun;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary of one reconciliation run. Deliberately contains counts only:
 * per-wallet balances stay in server logs, never in API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationSummary(
    @JsonProperty("run_id")
    UUID runId,

    @JsonProperty("started_at")
    Instant startedAt,

    @JsonProperty("finished_at")
    Instant finishedAt,

    @JsonProperty("wallets_checked")
    long walletsChecked,

    @JsonProperty("mismatched_count")
    long mismatchedCount,

    @JsonProperty("globally_balanced")
    boolean globallyBalanced,

    @JsonProperty("status")
    String status
) {
    public static ReconciliationSummary from(ReconciliationRun run) {
        return new ReconciliationSummary(
            run.getId(),
            run.getStartedAt(),
            run.getFinishedAt(),
            run.getWalletsChecked(),
            run.getMismatchedCount(),
            run.isGloballyBalanced(),
            run.getStatus()
        );
    }
}
