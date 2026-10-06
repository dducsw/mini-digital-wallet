package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.WalletResponse;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.exception.WalletNotFoundException;
import com.minidigitalwallet.repository.WalletRepository;
import com.minidigitalwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public WalletResponse getWalletBalance(UUID id) {
        return getWallet(id);
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
        Wallet wallet = getWalletEntity(id);
        WalletStatus newStatus = request.status();

        if (wallet.getStatus() == newStatus) {
            return WalletResponse.from(wallet);
        }

        if (wallet.isClosed()) {
            throw new AppException(
                "Cannot change status of a CLOSED wallet",
                HttpStatus.BAD_REQUEST,
                "wallet-already-closed"
            );
        }

        if (newStatus == WalletStatus.CLOSED && wallet.getAvailableBalance() > 0) {
            throw new AppException(
                "Cannot close wallet with remaining balance: " + wallet.getAvailableBalance() + " cents",
                HttpStatus.BAD_REQUEST,
                "wallet-balance-non-zero"
            );
        }

        wallet.setStatus(newStatus);
        Wallet updated = walletRepository.save(wallet);
        return WalletResponse.from(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWalletEntity(UUID id) {
        return walletRepository.findById(id)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }

    @Override
    @Transactional
    public Wallet getWalletEntityForUpdate(UUID id) {
        return walletRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }
}
