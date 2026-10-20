package com.minidigitalwallet.dto.projection;

import java.util.UUID;

/**
 * Read model for the reconciliation audit: the stored wallet snapshot versus
 * the balance derived from its immutable ledger entries.
 */
public record WalletBalance(UUID walletId, Long storedBalance, Long derivedBalance) {
}
