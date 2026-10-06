package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerFacade {

    private final LedgerService ledgerService;

    @Retryable(
        retryFor = {
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public LedgerTransaction executeTransferWithRetry(
        String referenceId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        Long amount,
        TransactionType transactionType,
        String description
    ) {
        log.debug("LedgerFacade invoking executeTransfer: ref={}", referenceId);
        return ledgerService.executeTransfer(
            referenceId,
            sourceWalletId,
            destinationWalletId,
            amount,
            transactionType,
            description
        );
    }
}
