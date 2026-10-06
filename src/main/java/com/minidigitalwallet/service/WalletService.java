package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.Wallet;

import java.util.List;
import java.util.UUID;

public interface WalletService {

    WalletResponse createWallet(CreateWalletRequest request);

    WalletResponse getWallet(UUID id);

    WalletResponse getWalletBalance(UUID id);

    List<WalletResponse> getWalletsByUserId(String userId);

    WalletResponse updateStatus(UUID id, UpdateWalletStatusRequest request);

    Wallet getWalletEntity(UUID id);

    Wallet getWalletEntityForUpdate(UUID id);
}
