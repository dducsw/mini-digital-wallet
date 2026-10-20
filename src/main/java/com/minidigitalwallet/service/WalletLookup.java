package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.response.RecipientPreview;

import java.util.UUID;

/**
 * Narrow read boundary (ISP): TransferService and LedgerController
 * only need lookups, never wallet creation.
 */
public interface WalletLookup {

    String getWalletOwnerId(UUID id);

    UUID resolveWalletIdByAccountNumber(String accountNumber);

    /** Safe, balance-free view used by the P2P recipient lookup. */
    RecipientPreview getRecipientPreview(String accountNumber);
}
