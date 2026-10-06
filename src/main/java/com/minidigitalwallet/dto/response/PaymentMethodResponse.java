package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.CardProvider;
import com.minidigitalwallet.entity.enums.MethodType;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethodResponse(
    @JsonProperty("id")
    UUID id,

    @JsonProperty("owner_id")
    String ownerId,

    @JsonProperty("method_type")
    MethodType methodType,

    @JsonProperty("provider")
    CardProvider provider,

    @JsonProperty("masked_number")
    String maskedNumber,

    @JsonProperty("holder_name")
    String holderName,

    @JsonProperty("payment_token")
    String paymentToken,

    @JsonProperty("is_default")
    boolean isDefault,

    @JsonProperty("status")
    PaymentMethodStatus status,

    @JsonProperty("created_at")
    Instant createdAt,

    @JsonProperty("updated_at")
    Instant updatedAt
) {
    public static PaymentMethodResponse from(PaymentMethod pm) {
        return new PaymentMethodResponse(
            pm.getId(),
            pm.getOwnerId(),
            pm.getMethodType(),
            pm.getProvider(),
            pm.getMaskedNumber(),
            pm.getHolderName(),
            pm.getPaymentToken(),
            pm.isDefault(),
            pm.getStatus(),
            pm.getCreatedAt(),
            pm.getUpdatedAt()
        );
    }
}
