package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.enums.WalletStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWalletStatusRequest(
    @NotNull(message = "status is required")
    @JsonProperty("status")
    WalletStatus status
) {
}
