package com.minidigitalwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidigitalwallet.dto.request.LinkCardRequest;
import com.minidigitalwallet.dto.response.PaymentMethodResponse;
import com.minidigitalwallet.entity.enums.CardProvider;
import com.minidigitalwallet.entity.enums.MethodType;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.entity.enums.UserRole;
import com.minidigitalwallet.exception.GlobalExceptionHandler;
import com.minidigitalwallet.exception.PaymentMethodNotFoundException;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.PaymentMethodService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = PaymentMethodController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.minidigitalwallet.security.SecurityConfig.class)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PaymentMethodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentMethodService paymentMethodService;

    private final UUID ownerId = UUID.randomUUID();
    private final String owner = ownerId.toString();

    @BeforeEach
    void setAuth() {
        UserPrincipal principal = UserPrincipal.create(ownerId, "alice", UserRole.USER);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/payment-methods should return 201 Created on valid card linking")
    void linkCard_Success() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentMethodResponse response = new PaymentMethodResponse(
            id, "USER_987654", MethodType.DEBIT_CARD, CardProvider.VISA,
            "**** **** **** 4242", "NGUYEN VAN A", "tok_mock_visa_4242",
            true, PaymentMethodStatus.ACTIVE, Instant.now(), Instant.now()
        );

        when(paymentMethodService.linkCard(any(LinkCardRequest.class))).thenReturn(response);

        LinkCardRequest request = new LinkCardRequest(
            "USER_987654", MethodType.DEBIT_CARD, CardProvider.VISA,
            "4242424242424242", "Nguyen Van A", true
        );

        mockMvc.perform(post("/api/v1/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.owner_id").value("USER_987654"))
            .andExpect(jsonPath("$.masked_number").value("**** **** **** 4242"))
            .andExpect(jsonPath("$.payment_token").value("tok_mock_visa_4242"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/payment-methods should return 400 Bad Request on invalid card number")
    void linkCard_InvalidCardNumber_Returns400() throws Exception {
        LinkCardRequest request = new LinkCardRequest(
            "USER_987654", MethodType.DEBIT_CARD, CardProvider.VISA,
            "123", "Nguyen Van A", true
        );

        mockMvc.perform(post("/api/v1/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://minidigitalwallet.com/errors/validation-error"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/payment-methods?owner_id=... should return list of payment methods")
    void getPaymentMethods_Success() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentMethodResponse response = new PaymentMethodResponse(
            id, owner, MethodType.DEBIT_CARD, CardProvider.VISA,
            "**** **** **** 4242", "NGUYEN VAN A", "tok_mock_visa_4242",
            true, PaymentMethodStatus.ACTIVE, Instant.now(), Instant.now()
        );

        when(paymentMethodService.getPaymentMethodsByOwner(owner)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/payment-methods")
                .param("owner_id", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].masked_number").value("**** **** **** 4242"));
    }

    @Test
    @DisplayName("DELETE /api/v1/payment-methods/{id} should return 204 No Content on revocation")
    void revokePaymentMethod_Success() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentMethodResponse existing = new PaymentMethodResponse(
            id, owner, MethodType.DEBIT_CARD, CardProvider.VISA,
            "**** **** **** 4242", "NGUYEN VAN A", "tok_mock_visa_4242",
            true, PaymentMethodStatus.ACTIVE, Instant.now(), Instant.now()
        );
        when(paymentMethodService.getPaymentMethod(id)).thenReturn(existing);
        doNothing().when(paymentMethodService).revokePaymentMethod(id);

        mockMvc.perform(delete("/api/v1/payment-methods/{id}", id))
            .andExpect(status().isNoContent());

        verify(paymentMethodService, times(1)).revokePaymentMethod(id);
    }

    @Test
    @DisplayName("DELETE /api/v1/payment-methods/{id} should return 404 when payment method not found")
    void revokePaymentMethod_NotFound_Returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentMethodService.getPaymentMethod(id)).thenThrow(new PaymentMethodNotFoundException(id));

        mockMvc.perform(delete("/api/v1/payment-methods/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://minidigitalwallet.com/errors/payment-method-not-found"))
            .andExpect(jsonPath("$.status").value(404));
    }
}
