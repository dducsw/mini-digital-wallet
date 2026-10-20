package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.domain.model.AccountNumber;
import com.minidigitalwallet.dto.request.CreateWalletRequest;
import com.minidigitalwallet.dto.request.UpdateWalletStatusRequest;
import com.minidigitalwallet.dto.response.RecipientPreview;
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
            .accountNumber(generateAccountNumber())
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
        // OCP: transition rules live in Wallet.transitionTo, service only delegates.
        wallet.transitionTo(request.status());
        Wallet updated = walletRepository.save(wallet);
        return WalletResponse.from(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public String getWalletOwnerId(UUID id) {
        return requireWallet(id).getUserId();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID resolveWalletIdByAccountNumber(String accountNumber) {
        return requireWalletByAccountNumber(accountNumber).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipientPreview getRecipientPreview(String accountNumber) {
        return RecipientPreview.from(requireWalletByAccountNumber(accountNumber));
    }

    private Wallet requireWalletByAccountNumber(String raw) {
        AccountNumber acct = AccountNumber.of(raw);
        return walletRepository.findByAccountNumber(acct.digits())
            .orElseThrow(() -> new com.minidigitalwallet.exception.AppException(
                "Wallet not found for account number: " + acct.digits(),
                org.springframework.http.HttpStatus.NOT_FOUND, "wallet-not-found"));
    }

    /** 12 digits: prefix 8820 + 8 random digits, retry until unique. */
    private String generateAccountNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = AccountNumber.random();
            if (!walletRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new DuplicateRequestException("Could not generate unique account number, please retry");
    }

    private Wallet requireWallet(UUID id) {
        return walletRepository.findById(id)
            .orElseThrow(() -> new WalletNotFoundException(id));
    }
}
