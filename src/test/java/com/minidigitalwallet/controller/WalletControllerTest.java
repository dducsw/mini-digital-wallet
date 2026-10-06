package com.minidigitalwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.GlobalExceptionHandler;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WalletController.class)
@Import(GlobalExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WalletService walletService;

    @Test
    @DisplayName("POST /api/v1/wallets should return 201 Created on valid request")
    void createWallet_Success() throws Exception {
        UUID id = UUID.randomUUID();
        WalletResponse response = new WalletResponse(
            id, "USER_987654", WalletType.USER, WalletType.USER,
            WalletStatus.ACTIVE, WalletStatus.ACTIVE,
            0L, 0L, "USD", 0L, Instant.now(), Instant.now()
        );

        when(walletService.createWallet(any(CreateWalletRequest.class))).thenReturn(response);

        CreateWalletRequest request = new CreateWalletRequest("USER_987654", "USD", WalletType.USER);

        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.owner_id").value("USER_987654"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.available_balance").value(0))
            .andExpect(jsonPath("$.ledger_balance").value(0))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/wallets should return 400 Bad Request with RFC 7807 when owner_id is missing")
    void createWallet_MissingOwnerId_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"USD\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("https://minidigitalwallet.com/errors/validation-error"))
            .andExpect(jsonPath("$.title").value("Validation Error"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/wallets should return 409 Conflict with RFC 7807 on duplicate wallet")
    void createWallet_Duplicate_Returns409() throws Exception {
        when(walletService.createWallet(any(CreateWalletRequest.class)))
            .thenThrow(new DuplicateRequestException("Wallet already exists for owner USER_987654 with currency USD"));

        CreateWalletRequest request = new CreateWalletRequest("USER_987654", "USD", WalletType.USER);

        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("https://minidigitalwallet.com/errors/duplicate-request"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Wallet already exists for owner USER_987654 with currency USD"));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} should return 200 OK when found")
    void getWallet_Success() throws Exception {
        UUID id = UUID.randomUUID();
        WalletResponse response = new WalletResponse(
            id, "USER_987654", WalletType.USER, WalletType.USER,
            WalletStatus.ACTIVE, WalletStatus.ACTIVE,
            25000L, 25000L, "USD", 1L, Instant.now(), Instant.now()
        );

        when(walletService.getWallet(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.available_balance").value(25000))
            .andExpect(jsonPath("$.ledger_balance").value(25000));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} should return 404 Not Found with RFC 7807 when missing")
    void getWallet_NotFound_Returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(walletService.getWallet(id)).thenThrow(new WalletNotFoundException(id));

        mockMvc.perform(get("/api/v1/wallets/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://minidigitalwallet.com/errors/wallet-not-found"))
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/balance should return 200 OK with snapshot balances")
    void getWalletBalance_Success() throws Exception {
        UUID id = UUID.randomUUID();
        WalletResponse response = new WalletResponse(
            id, "USER_987654", WalletType.USER, WalletType.USER,
            WalletStatus.ACTIVE, WalletStatus.ACTIVE,
            50000L, 50000L, "USD", 2L, Instant.now(), Instant.now()
        );

        when(walletService.getWalletBalance(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/{id}/balance", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.available_balance").value(50000));
    }

    @Test
    @DisplayName("PATCH /api/v1/wallets/{id}/status should return 200 OK on status update")
    void updateWalletStatus_Success() throws Exception {
        UUID id = UUID.randomUUID();
        WalletResponse response = new WalletResponse(
            id, "USER_987654", WalletType.USER, WalletType.USER,
            WalletStatus.FROZEN, WalletStatus.FROZEN,
            0L, 0L, "USD", 1L, Instant.now(), Instant.now()
        );

        when(walletService.updateStatus(eq(id), any(UpdateWalletStatusRequest.class))).thenReturn(response);

        UpdateWalletStatusRequest request = new UpdateWalletStatusRequest(WalletStatus.FROZEN);

        mockMvc.perform(patch("/api/v1/wallets/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FROZEN"));
    }
}
