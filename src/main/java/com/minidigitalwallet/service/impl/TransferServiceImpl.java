package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.service.LedgerFacade;
import com.minidigitalwallet.service.PaymentMethodService;
import com.minidigitalwallet.service.TransferService;
import com.minidigitalwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final LedgerFacade ledgerFacade;
    private final WalletService walletService;
    private final PaymentMethodService paymentMethodService;

    @Override
    public TransferResponse topUp(TopUpRequest request, String idempotencyKey) {
        String ownerId = walletService.getWalletOwnerId(request.walletId());
        PaymentMethod card = paymentMethodService.requireOwnedCard(request.paymentMethodId(), ownerId);

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
        String ownerId = walletService.getWalletOwnerId(request.walletId());
        PaymentMethod card = paymentMethodService.requireOwnedCard(request.paymentMethodId(), ownerId);

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
        // Ví không tồn tại -> LedgerService ném WalletNotFoundException khi lock, khỏi SELECT thừa ở đây.
        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            referenceOr(idempotencyKey, "P2P"),
            request.sourceWalletId(),
            request.destinationWalletId(),
            request.amount(),
            TransactionType.P2P_TRANSFER,
            request.description() != null ? request.description() : "P2P transfer"
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse reverse(UUID originalTransactionId, String idempotencyKey, String description) {
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
}
