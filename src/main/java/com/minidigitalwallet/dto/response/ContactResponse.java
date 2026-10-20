package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.Contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
    @JsonProperty("id")
    UUID id,

    @JsonProperty("account_number")
    String accountNumber,

    @JsonProperty("nickname")
    String nickname,

    @JsonProperty("is_favorite")
    Boolean isFavorite,

    @JsonProperty("created_at")
    Instant createdAt
) {
    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
            contact.getId(),
            contact.getAccountNumber(),
            contact.getNickname(),
            contact.getIsFavorite(),
            contact.getCreatedAt()
        );
    }
}
