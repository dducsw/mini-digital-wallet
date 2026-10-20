package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateContactRequest(
    @NotBlank(message = "account_number is required")
    @Pattern(regexp = "^[0-9 ]{12,14}$", message = "account_number must be 12 digits")
    @JsonProperty("account_number")
    String accountNumber,

    @NotBlank(message = "nickname is required")
    @JsonProperty("nickname")
    String nickname
) {
}
