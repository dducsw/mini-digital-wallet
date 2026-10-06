package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.exception.PaymentMethodNotFoundException;
import com.minidigitalwallet.repository.PaymentMethodRepository;
import com.minidigitalwallet.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    @Transactional
    public PaymentMethodResponse linkCard(LinkCardRequest request) {
        String cardNumber = request.cardNumber().trim();
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        String maskedNumber = "**** **** **** " + last4;
        String token = "tok_mock_" + request.provider().name().toLowerCase() + "_" + last4;

        boolean isDefault = request.isDefault();

        // If user has no existing active payment methods, make this one default
        List<PaymentMethod> existingActive = paymentMethodRepository.findByOwnerIdAndStatus(
            request.ownerId(), PaymentMethodStatus.ACTIVE
        );
        if (existingActive.isEmpty()) {
            isDefault = true;
        }

        PaymentMethod paymentMethod = PaymentMethod.builder()
            .ownerId(request.ownerId())
            .methodType(request.methodType())
            .provider(request.provider())
            .maskedNumber(maskedNumber)
            .holderName(request.holderName().trim().toUpperCase())
            .paymentToken(token)
            .isDefault(isDefault)
            .status(PaymentMethodStatus.ACTIVE)
            .build();

        PaymentMethod saved = paymentMethodRepository.save(paymentMethod);

        if (isDefault) {
            paymentMethodRepository.resetDefaultFlags(saved.getOwnerId(), saved.getId());
        }

        return PaymentMethodResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsByOwner(String ownerId) {
        return paymentMethodRepository.findByOwnerIdAndStatus(ownerId, PaymentMethodStatus.ACTIVE)
            .stream()
            .map(PaymentMethodResponse::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethod(UUID id) {
        return paymentMethodRepository.findByIdAndStatus(id, PaymentMethodStatus.ACTIVE)
            .map(PaymentMethodResponse::from)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }

    @Override
    @Transactional
    public void revokePaymentMethod(UUID id) {
        PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndStatus(id, PaymentMethodStatus.ACTIVE)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));

        paymentMethod.revoke();
        paymentMethodRepository.save(paymentMethod);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethod getPaymentMethodEntity(UUID id) {
        return paymentMethodRepository.findByIdAndStatus(id, PaymentMethodStatus.ACTIVE)
            .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }
}
