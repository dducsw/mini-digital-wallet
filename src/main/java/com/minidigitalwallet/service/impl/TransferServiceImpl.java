package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.PaymentMethodNotFoundException;
import com.minidigitalwallet.repository.PaymentMethodRepository;
import com.minidigitalwallet.service.LedgerFacade;
import com.minidigitalwallet.service.TransferService;
import com.minidigitalwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final LedgerFacade ledgerFacade;
    private final WalletService walletService;
    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    public TransferResponse topUp(TopUpRequest request, String idempotencyKey) {
        Wallet wallet = walletService.getWalletEntity(request.walletId());

        PaymentMethod card = paymentMethodRepository.findByIdAndStatus(request.paymentMethodId(), PaymentMethodStatus.ACTIVE)
            .orElseThrow(() -> new PaymentMethodNotFoundException(request.paymentMethodId()));

        if (!card.getOwnerId().equals(wallet.getUserId())) {
            throw new AppException(
                "Payment method does not belong to the wallet owner",
                HttpStatus.FORBIDDEN,
                "payment-method-forbidden"
            );
        }

        String refId = idempotencyKey != null && !idempotencyKey.isBlank()
            ? idempotencyKey
            : "TOPUP-" + UUID.randomUUID();

        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            refId,
            SYS_EXTERNAL_SETTLEMENT_ID,
            wallet.getId(),
            request.amount(),
            TransactionType.TOPUP,
            request.description() != null ? request.description() : "Top-up via card " + card.getMaskedNumber()
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse withdraw(WithdrawRequest request, String idempotencyKey) {
        Wallet wallet = walletService.getWalletEntity(request.walletId());

        PaymentMethod card = paymentMethodRepository.findByIdAndStatus(request.paymentMethodId(), PaymentMethodStatus.ACTIVE)
            .orElseThrow(() -> new PaymentMethodNotFoundException(request.paymentMethodId()));

        if (!card.getOwnerId().equals(wallet.getUserId())) {
            throw new AppException(
                "Payment method does not belong to the wallet owner",
                HttpStatus.FORBIDDEN,
                "payment-method-forbidden"
            );
        }

        String refId = idempotencyKey != null && !idempotencyKey.isBlank()
            ? idempotencyKey
            : "WITHDRAW-" + UUID.randomUUID();

        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            refId,
            wallet.getId(),
            SYS_EXTERNAL_SETTLEMENT_ID,
            request.amount(),
            TransactionType.WITHDRAW,
            request.description() != null ? request.description() : "Withdrawal to card " + card.getMaskedNumber()
        );

        return TransferResponse.from(tx);
    }

    @Override
    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        String refId = idempotencyKey != null && !idempotencyKey.isBlank()
            ? idempotencyKey
            : "P2P-" + UUID.randomUUID();

        LedgerTransaction tx = ledgerFacade.executeTransferWithRetry(
            refId,
            request.sourceWalletId(),
            request.destinationWalletId(),
            request.amount(),
            TransactionType.P2P_TRANSFER,
            request.description() != null ? request.description() : "P2P transfer"
        );

        return TransferResponse.from(tx);
    }
}
