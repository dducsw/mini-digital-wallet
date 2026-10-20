package com.minidigitalwallet.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.WalletType;

/**
 * Minimal projection returned by the P2P recipient lookup.
 *
 * Any authenticated user may resolve an account number, so this record
 * deliberately carries no balance, owner id, version or timestamps: a full
 * WalletResponse here would let one user enumerate 12-digit account numbers
 * and read other people's balances.
 */
public record RecipientPreview(
    @JsonProperty("account_number") String accountNumber,
    @JsonProperty("wallet_type") WalletType walletType,
    @JsonProperty("currency") String currency
) {
    public static RecipientPreview from(Wallet wallet) {
        return new RecipientPreview(
            wallet.getAccountNumber(),
            wallet.getWalletType(),
            wallet.getCurrency()
        );
    }
}
