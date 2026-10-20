package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;

import java.util.List;
import java.util.UUID;

public interface WalletService extends WalletLookup {

    WalletResponse createWallet(CreateWalletRequest request);

    WalletResponse getWallet(UUID id);

    default WalletResponse getWalletBalance(UUID id) {
        return getWallet(id);
    }

    List<WalletResponse> getWalletsByUserId(String userId);

    WalletResponse updateStatus(UUID id, UpdateWalletStatusRequest request);
}
