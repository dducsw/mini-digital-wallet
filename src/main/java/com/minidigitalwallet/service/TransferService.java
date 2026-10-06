package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;

import java.util.UUID;

public interface TransferService {

    UUID SYS_EXTERNAL_SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID SYS_CASH_IN_TRANSIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID SYS_FEE_REVENUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    TransferResponse topUp(TopUpRequest request, String idempotencyKey);

    TransferResponse withdraw(WithdrawRequest request, String idempotencyKey);

    TransferResponse transfer(TransferRequest request, String idempotencyKey);
}
