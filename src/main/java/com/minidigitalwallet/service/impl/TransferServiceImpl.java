package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.service.CallerProvider;
import com.minidigitalwallet.service.LedgerFacade;
import com.minidigitalwallet.service.PaymentMethodOwnership;
import com.minidigitalwallet.service.TransactionLimitValidator;
import com.minidigitalwallet.service.TransferDestination;
import com.minidigitalwallet.service.TransferService;
import com.minidigitalwallet.service.WalletLookup;
import com.minidigitalwallet.security.TransactionPinValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final LedgerFacade ledgerFacade;
    private final WalletLookup wallets;
    private final PaymentMethodOwnership cards;
    private final CallerProvider caller;
    private final TransactionPinValidator pinValidator;
    private final TransactionLimitValidator limitValidator;

    @Override
    public TransferResponse topUp(TopUpRequest request, String idempotencyKey) {
        String ownerId = wallets.getWalletOwnerId(request.walletId());
        requireCallerOwns(ownerId);
        limitValidator.validateAmount(request.amount());
        PaymentMethod card = cards.requireOwnedCard(request.paymentMethodId(), ownerId);

        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            referenceOr(idempotencyKey, "TOPUP"),
            SYS_EXTERNAL_SETTLEMENT_ID,
            request.walletId(),
            request.amount(),
            TransactionType.TOPUP,
            request.description() != null ? request.description() : "Top-up via card " + card.getMaskedNumber()
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse withdraw(WithdrawRequest request, String idempotencyKey) {
        return withdraw(request, idempotencyKey, null);
    }

    @Override
    public TransferResponse withdraw(WithdrawRequest request, String idempotencyKey, String pin) {
        String ownerId = wallets.getWalletOwnerId(request.walletId());
        requireCallerOwns(ownerId);
        pinValidator.validate(ownerId, pin);
        limitValidator.validateAmount(request.amount());
        PaymentMethod card = cards.requireOwnedCard(request.paymentMethodId(), ownerId);

        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            referenceOr(idempotencyKey, "WITHDRAW"),
            request.walletId(),
            SYS_EXTERNAL_SETTLEMENT_ID,
            request.amount(),
            TransactionType.WITHDRAW,
            request.description() != null ? request.description() : "Withdrawal to card " + card.getMaskedNumber()
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        return transfer(request, idempotencyKey, null);
    }

    @Override
    public TransferResponse transfer(TransferRequest request, String idempotencyKey, String pin) {
        String ownerId = wallets.getWalletOwnerId(request.sourceWalletId());
        requireCallerOwns(ownerId);
        pinValidator.validate(ownerId, pin);
        limitValidator.validateAmount(request.amount());
        UUID destId = TransferDestination.resolve(request, wallets);
        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            referenceOr(idempotencyKey, "P2P"),
            request.sourceWalletId(),
            destId,
            request.amount(),
            TransactionType.P2P_TRANSFER,
            request.description() != null ? request.description() : "P2P transfer"
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse reverse(UUID originalTransactionId, String idempotencyKey, String description) {
        // P0 BOLA fix: caller must own a wallet involved in the original tx.
        requireCallerInvolved(ledgerFacade.getTransaction(originalTransactionId));
        LedgerTransaction tx = ledgerFacade.reverseWithRetry(
            originalTransactionId,
            referenceOr(idempotencyKey, "REVERSAL"),
            description
        );

        return TransferResponse.from(tx);
    }

    private String referenceOr(String idempotencyKey, String prefix) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
            ? idempotencyKey
            : prefix + "-" + UUID.randomUUID();
    }

    private void requireCallerOwns(String walletOwnerId) {
        String callerId = caller.currentUserIdOrNull();
        if (callerId == null) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        if (!callerId.equals(walletOwnerId)) {
            throw new AppException("Access denied: wallet does not belong to current user",
                HttpStatus.FORBIDDEN, "access-denied");
        }
    }

    /** Reverse needs looser rule than single-wallet writes: sender OR receiver may reverse. */
    private void requireCallerInvolved(LedgerTransaction original) {
        String callerId = caller.currentUserIdOrNull();
        if (callerId == null) {
            throw new AppException("Authentication required",
                HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        if (original.getEntries() == null || original.getEntries().isEmpty()) {
            throw new AppException("Access denied: transaction does not involve current user",
                HttpStatus.FORBIDDEN, "access-denied");
        }
        for (com.minidigitalwallet.entity.LedgerEntry e : original.getEntries()) {
            try {
                if (callerId.equals(wallets.getWalletOwnerId(e.getWalletId()))) {
                    return;
                }
            } catch (Exception ignored) {
                // System wallets (SYS_*) have no user owner -> skip.
            }
        }
        throw new AppException("Access denied: transaction does not involve current user",
            HttpStatus.FORBIDDEN, "access-denied");
    }
}
