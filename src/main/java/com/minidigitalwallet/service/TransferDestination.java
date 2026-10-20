package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.TransferRequest;

import java.util.UUID;

/**
 * Resolves P2P destination (SRP/OCP lite): 12-digit account number preferred,
 * legacy UUID fallback. TransferService no longer branches here.
 */
public final class TransferDestination {

    private TransferDestination() {
    }

    public static UUID resolve(TransferRequest request, WalletLookup wallets) {
        String acct = request.destinationAccountNumber();
        if (acct != null && !acct.isBlank()) {
            return wallets.resolveWalletIdByAccountNumber(acct);
        }
        if (request.destinationWalletId() == null) {
            throw new IllegalArgumentException(
                "destination_wallet_id or destination_account_number is required");
        }
        return request.destinationWalletId();
    }
}
