package com.minidigitalwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    @Test
    @DisplayName("POST /api/v1/transfers/topup returns 201 Created")
    void topUp_ReturnsCreated() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(walletId, cardId, 5000L, "Topup");

        TransferResponse response = new TransferResponse(
            UUID.randomUUID(),
            "REF-TOPUP-1",
            TransactionType.TOPUP,
            5000L,
            "USD",
            "Topup",
            Instant.now()
        );

        when(transferService.topUp(any(TopUpRequest.class), eq("IDEM-1"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transfers/topup")
                .header("Idempotency-Key", "IDEM-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(5000))
            .andExpect(jsonPath("$.transaction_type").value("TOPUP"));
    }

    @Test
    @DisplayName("POST /api/v1/transfers/withdraw returns 201 Created")
    void withdraw_ReturnsCreated() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        WithdrawRequest request = new WithdrawRequest(walletId, cardId, 3000L, "Withdraw");

        TransferResponse response = new TransferResponse(
            UUID.randomUUID(),
            "REF-WITHDRAW-1",
            TransactionType.WITHDRAW,
            3000L,
            "USD",
            "Withdraw",
            Instant.now()
        );

        when(transferService.withdraw(any(WithdrawRequest.class), eq("IDEM-2"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transfers/withdraw")
                .header("Idempotency-Key", "IDEM-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(3000))
            .andExpect(jsonPath("$.transaction_type").value("WITHDRAW"));
    }

    @Test
    @DisplayName("POST /api/v1/transfers/p2p returns 201 Created")
    void p2p_ReturnsCreated() throws Exception {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(srcId, dstId, 1000L, "P2P");

        TransferResponse response = new TransferResponse(
            UUID.randomUUID(),
            "REF-P2P-1",
            TransactionType.P2P_TRANSFER,
            1000L,
            "USD",
            "P2P",
            Instant.now()
        );

        when(transferService.transfer(any(TransferRequest.class), eq("IDEM-3"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transfers/p2p")
                .header("Idempotency-Key", "IDEM-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(1000))
            .andExpect(jsonPath("$.transaction_type").value("P2P_TRANSFER"));
    }
}
