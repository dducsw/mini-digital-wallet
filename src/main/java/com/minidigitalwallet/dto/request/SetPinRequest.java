package com.minidigitalwallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "PIN must be exactly 6 digits")
    String pin
) {
}
