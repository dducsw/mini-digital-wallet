package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
import com.minidigitalwallet.entity.PaymentMethod;

import java.util.List;
import java.util.UUID;

public interface PaymentMethodService {

    PaymentMethodResponse linkCard(LinkCardRequest request);

    List<PaymentMethodResponse> getPaymentMethodsByOwner(String ownerId);

    PaymentMethodResponse getPaymentMethod(UUID id);

    void revokePaymentMethod(UUID id);

    PaymentMethod getPaymentMethodEntity(UUID id);
}
