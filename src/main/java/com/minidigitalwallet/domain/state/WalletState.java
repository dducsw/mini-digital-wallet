package com.minidigitalwallet.domain.state;

import com.minidigitalwallet.domain.model.Money;
import com.minidigitalwallet.entity.Wallet;

/**
 * State pattern interface encapsulating business behaviors and allowed transitions
 * for a Wallet based on its lifecycle status.
 */
public interface WalletState {

    /** Performs debit on the wallet according to current state. */
    void debit(Wallet wallet, Money money);

    /** Performs credit on the wallet according to current state. */
    void credit(Wallet wallet, Money money);

    /** Freezes the wallet. */
    void freeze(Wallet wallet);

    /** Unfreezes the wallet. */
    void unfreeze(Wallet wallet);

    /** Closes the wallet permanently. */
    void close(Wallet wallet);
}
