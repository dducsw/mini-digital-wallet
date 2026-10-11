package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.CardProvider;
import com.minidigitalwallet.entity.enums.MethodType;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.exception.PaymentMethodNotFoundException;
import com.minidigitalwallet.repository.PaymentMethodRepository;
import com.minidigitalwallet.service.impl.PaymentMethodServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @InjectMocks
    private PaymentMethodServiceImpl paymentMethodService;

    private UUID methodId;
    private PaymentMethod mockPaymentMethod;

    @BeforeEach
    void setUp() {
        methodId = UUID.randomUUID();
        mockPaymentMethod = PaymentMethod.builder()
            .id(methodId)
            .ownerId("USER_987654")
            .methodType(MethodType.DEBIT_CARD)
            .provider(CardProvider.VISA)
            .maskedNumber("**** **** **** 4242")
            .holderName("NGUYEN VAN A")
            .paymentToken("tok_mock_visa_4242")
            .isDefault(true)
            .status(PaymentMethodStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("linkCard should tokenize and mask card without storing raw PAN")
    void linkCard_Success() {
        LinkCardRequest request = new LinkCardRequest(
            "USER_987654", MethodType.DEBIT_CARD, CardProvider.VISA,
            "4242424242424242", "Nguyen Van A", true
        );

        when(paymentMethodRepository.findByOwnerIdAndStatus("USER_987654", PaymentMethodStatus.ACTIVE))
            .thenReturn(Collections.emptyList());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> {
            PaymentMethod pm = invocation.getArgument(0);
            pm.setId(methodId);
            pm.setCreatedAt(Instant.now());
            pm.setUpdatedAt(Instant.now());
            return pm;
        });

        PaymentMethodResponse response = paymentMethodService.linkCard(request);

        assertThat(response).isNotNull();
        assertThat(response.maskedNumber()).isEqualTo("**** **** **** 4242");
        assertThat(response.paymentToken()).startsWith("tok_mock_visa_4242_");
        assertThat(response.holderName()).isEqualTo("NGUYEN VAN A");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.status()).isEqualTo(PaymentMethodStatus.ACTIVE);

        verify(paymentMethodRepository, times(1)).save(any(PaymentMethod.class));
        verify(paymentMethodRepository, times(1)).resetDefaultFlags(eq("USER_987654"), eq(methodId));
    }

    @Test
    @DisplayName("getPaymentMethodsByOwner should return active payment methods")
    void getPaymentMethodsByOwner_Success() {
        when(paymentMethodRepository.findByOwnerIdAndStatus("USER_987654", PaymentMethodStatus.ACTIVE))
            .thenReturn(List.of(mockPaymentMethod));

        List<PaymentMethodResponse> list = paymentMethodService.getPaymentMethodsByOwner("USER_987654");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).ownerId()).isEqualTo("USER_987654");
    }

    @Test
    @DisplayName("revokePaymentMethod should mark status as REVOKED")
    void revokePaymentMethod_Success() {
        when(paymentMethodRepository.findByIdAndStatus(methodId, PaymentMethodStatus.ACTIVE))
            .thenReturn(Optional.of(mockPaymentMethod));

        paymentMethodService.revokePaymentMethod(methodId);

        assertThat(mockPaymentMethod.getStatus()).isEqualTo(PaymentMethodStatus.REVOKED);
        assertThat(mockPaymentMethod.isDefault()).isFalse();
        verify(paymentMethodRepository, times(1)).save(mockPaymentMethod);
    }

    @Test
    @DisplayName("revokePaymentMethod should throw PaymentMethodNotFoundException when not found")
    void revokePaymentMethod_NotFound_ThrowsException() {
        UUID nonExistent = UUID.randomUUID();
        when(paymentMethodRepository.findByIdAndStatus(nonExistent, PaymentMethodStatus.ACTIVE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.revokePaymentMethod(nonExistent))
            .isInstanceOf(PaymentMethodNotFoundException.class);
    }
}
