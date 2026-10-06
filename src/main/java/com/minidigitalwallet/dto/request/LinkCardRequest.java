package com.minidigitalwallet.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.enums.CardProvider;
import com.minidigitalwallet.entity.enums.MethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LinkCardRequest(
    @NotBlank(message = "owner_id is required")
    @JsonProperty("owner_id")
    @JsonAlias({"user_id", "ownerId", "userId"})
    String ownerId,

    @NotNull(message = "method_type is required")
    @JsonProperty("method_type")
    @JsonAlias({"methodType"})
    MethodType methodType,

    @NotNull(message = "provider is required")
    @JsonProperty("provider")
    CardProvider provider,

    @NotBlank(message = "card_number is required")
    @Pattern(regexp = "^[0-9]{12,19}$", message = "Invalid card number format (must be 12-19 digits)")
    @JsonProperty("card_number")
    @JsonAlias({"cardNumber"})
    String cardNumber,

    @NotBlank(message = "holder_name is required")
    @JsonProperty("holder_name")
    @JsonAlias({"holderName"})
    String holderName,

    @JsonProperty("is_default")
    @JsonAlias({"isDefault"})
    Boolean isDefault
) {
    @Override
    public Boolean isDefault() {
        return isDefault != null && isDefault;
    }
}
