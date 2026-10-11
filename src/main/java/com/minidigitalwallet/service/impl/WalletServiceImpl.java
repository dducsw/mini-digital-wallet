package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        String currency = request.currency();
        String userId = request.ownerId();
        var walletType = request.walletType();

        if (walletRepository.existsByUserIdAndCurrencyAndWalletType(userId, currency, walletType)) {
            throw new DuplicateRequestException(
                "Wallet already exists for owner " + userId + " with currency " + currency
            );
        }

        Wallet wallet = Wallet.builder()
            .userId(userId)
            .walletType(walletType)
            .currency(currency)
            .availableBalance(0L)
            .ledgerBalance(0L)
            .status(WalletStatus.ACTIVE)
            .version(0L)
            .build();

        Wallet saved = walletRepository.save(wallet);
        return WalletResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID id) {
        return walletRepository.findById(id)
            .map(WalletResponse::from)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletResponse> getWalletsByUserId(String userId) {
        return walletRepository.findByUserId(userId)
            .stream()
            .map(WalletResponse::from)
            .toList();
    }

    @Override
    @Transactional
    public WalletResponse updateStatus(UUID id, UpdateWalletStatusRequest request) {
        Wallet wallet = requireWallet(id);
        WalletStatus newStatus = request.status();

        if (wallet.getStatus() == newStatus) {
            return WalletResponse.from(wallet);
        }

        // OCP: transition + invariants (CLOSED guard, non-zero balance) live in WalletState.
        // Service only maps requested status -> state behavior; new states need no edit here
        // beyond the mapping, and rules evolve inside state classes.
        switch (newStatus) {
            case ACTIVE -> wallet.unfreeze();
            case FROZEN -> wallet.freeze();
            case CLOSED -> wallet.close();
        }

        Wallet updated = walletRepository.save(wallet);
        return WalletResponse.from(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public String getWalletOwnerId(UUID id) {
        return requireWallet(id).getUserId();
    }

    private Wallet requireWallet(UUID id) {
        return walletRepository.findById(id)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }
}
