package com.minidigitalwallet.service;

import com.minidigitalwallet.entity.PaymentMethod;

import java.util.UUID;

/**
 * Narrow boundary (ISP): TransferService only needs card ownership check.
 */
public interface PaymentMethodOwnership {

    PaymentMethod requireOwnedCard(UUID cardId, String walletOwnerId);
}
