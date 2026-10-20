package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.TopUpRequest;
import com.minidigitalwallet.dto.request.TransferRequest;
import com.minidigitalwallet.dto.request.WithdrawRequest;
import com.minidigitalwallet.dto.response.TransferResponse;
import com.minidigitalwallet.entity.LedgerEntry;
import com.minidigitalwallet.entity.LedgerTransaction;
import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.Wallet;
import com.minidigitalwallet.entity.enums.EntryType;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import com.minidigitalwallet.entity.enums.TransactionType;
import com.minidigitalwallet.entity.enums.WalletStatus;
import com.minidigitalwallet.entity.enums.WalletType;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private LedgerFacade ledgerFacade;

    @Mock
    private WalletService walletService;

    @Mock
    private PaymentMethodService paymentMethodService;

    @Mock
    private com.minidigitalwallet.service.CallerProvider callerProvider;

    @Mock
    private com.minidigitalwallet.security.TransactionPinValidator pinValidator;

    @Mock
    private TransactionLimitValidator limitValidator;

    @InjectMocks
    private TransferServiceImpl transferService;

    private Wallet userWallet;
    private PaymentMethod userCard;

    @BeforeEach
    void setUp() {
        userWallet = Wallet.builder()
            .id(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
            .userId("USR_001")
            .walletType(WalletType.USER)
            .currency("USD")
            .availableBalance(5000L)
            .ledgerBalance(5000L)
            .status(WalletStatus.ACTIVE)
            .build();

        userCard = PaymentMethod.builder()
            .id(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))
            .ownerId("USR_001")
            .maskedNumber("**** 4242")
            .status(PaymentMethodStatus.ACTIVE)
            .build();

        transferService = new TransferServiceImpl(
            ledgerFacade, walletService, paymentMethodService, callerProvider, pinValidator, limitValidator);
        lenient().when(callerProvider.currentUserIdOrNull()).thenReturn("USR_001");
    }

    @Test
    @DisplayName("topUp: Successfully deposits funds from external settlement to user wallet")
    void topUp_Success() {
        TopUpRequest request = new TopUpRequest(userWallet.getId(), userCard.getId(), 2000L, "Topup test");

        LedgerTransaction mockTx = LedgerTransaction.builder()
            .id(UUID.randomUUID())
            .referenceId("TOPUP-123")
            .transactionType(TransactionType.TOPUP)
            .amount(2000L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        when(walletService.getWalletOwnerId(userWallet.getId())).thenReturn("USR_001");
        when(paymentMethodService.requireOwnedCard(userCard.getId(), "USR_001")).thenReturn(userCard);
        when(ledgerFacade.executeTransferWithRetry(
            any(),
            eq(TransferService.SYS_EXTERNAL_SETTLEMENT_ID),
            eq(userWallet.getId()),
            eq(2000L),
            eq(TransactionType.TOPUP),
            any()
        )).thenReturn(mockTx);

        TransferResponse response = transferService.topUp(request, "IDEM-TOPUP-1");

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(2000L);
        assertThat(response.transactionType()).isEqualTo(TransactionType.TOPUP);
    }

    @Test
    @DisplayName("topUp: Payment method of another user throws 403 Forbidden")
    void topUp_UnauthorizedCard_ThrowsForbidden() {
        PaymentMethod otherCard = PaymentMethod.builder()
            .id(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
            .ownerId("HACKER_USER")
            .maskedNumber("**** 9999")
            .status(PaymentMethodStatus.ACTIVE)
            .build();

        TopUpRequest request = new TopUpRequest(userWallet.getId(), otherCard.getId(), 2000L, "Hacked topup");

        when(walletService.getWalletOwnerId(userWallet.getId())).thenReturn("USR_001");
        when(paymentMethodService.requireOwnedCard(otherCard.getId(), "USR_001"))
            .thenThrow(new AppException("Payment method does not belong to the wallet owner",
                org.springframework.http.HttpStatus.FORBIDDEN, "payment-method-forbidden"));

        assertThatThrownBy(() -> transferService.topUp(request, "IDEM-HACK"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Payment method does not belong to the wallet owner");
    }

    @Test
    @DisplayName("withdraw: Successfully withdraws funds from user wallet to external settlement")
    void withdraw_Success() {
        WithdrawRequest request = new WithdrawRequest(userWallet.getId(), userCard.getId(), 1500L, "Withdraw test");

        LedgerTransaction mockTx = LedgerTransaction.builder()
            .id(UUID.randomUUID())
            .referenceId("WITHDRAW-123")
            .transactionType(TransactionType.WITHDRAW)
            .amount(1500L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        when(walletService.getWalletOwnerId(userWallet.getId())).thenReturn("USR_001");
        when(paymentMethodService.requireOwnedCard(userCard.getId(), "USR_001")).thenReturn(userCard);
        when(ledgerFacade.executeTransferWithRetry(
            any(),
            eq(userWallet.getId()),
            eq(TransferService.SYS_EXTERNAL_SETTLEMENT_ID),
            eq(1500L),
            eq(TransactionType.WITHDRAW),
            any()
        )).thenReturn(mockTx);

        TransferResponse response = transferService.withdraw(request, "IDEM-WITHDRAW-1");

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(1500L);
        assertThat(response.transactionType()).isEqualTo(TransactionType.WITHDRAW);
    }

    @Test
    @DisplayName("transfer: Successfully initiates P2P transfer between two wallets")
    void transfer_Success() {
        UUID destWalletId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        TransferRequest request = new TransferRequest(userWallet.getId(), destWalletId, 1000L, "P2P test");

        LedgerTransaction mockTx = LedgerTransaction.builder()
            .id(UUID.randomUUID())
            .referenceId("P2P-123")
            .transactionType(TransactionType.P2P_TRANSFER)
            .amount(1000L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        when(walletService.getWalletOwnerId(userWallet.getId())).thenReturn("USR_001");
        when(ledgerFacade.executeTransferWithRetry(
            any(),
            eq(userWallet.getId()),
            eq(destWalletId),
            eq(1000L),
            eq(TransactionType.P2P_TRANSFER),
            any()
        )).thenReturn(mockTx);

        TransferResponse response = transferService.transfer(request, "IDEM-P2P-1");

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(1000L);
        assertThat(response.transactionType()).isEqualTo(TransactionType.P2P_TRANSFER);
    }

    @Test
    @DisplayName("reverse: owner involved in original tx can reverse")
    void reverse_OwnerInvolved_Success() {
        UUID origId = UUID.randomUUID();
        UUID destId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        LedgerTransaction original = LedgerTransaction.builder()
            .id(origId)
            .referenceId("P2P-1")
            .transactionType(TransactionType.P2P_TRANSFER)
            .amount(1000L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();
        original.addEntry(LedgerEntry.builder()
            .walletId(userWallet.getId()).entryType(EntryType.DEBIT)
            .amount(1000L).lineNo(1).balanceAfter(4000L).build());
        original.addEntry(LedgerEntry.builder()
            .walletId(destId).entryType(EntryType.CREDIT)
            .amount(1000L).lineNo(2).balanceAfter(1000L).build());

        LedgerTransaction reversal = LedgerTransaction.builder()
            .id(UUID.randomUUID())
            .referenceId("REV-1")
            .transactionType(TransactionType.REVERSAL)
            .amount(1000L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        when(callerProvider.currentUserIdOrNull()).thenReturn("USR_001");
        when(walletService.getWalletOwnerId(userWallet.getId())).thenReturn("USR_001");
        when(ledgerFacade.getTransaction(origId)).thenReturn(original);
        when(ledgerFacade.reverseWithRetry(eq(origId), any(), any())).thenReturn(reversal);

        TransferResponse response = transferService.reverse(origId, "IDEM-REV-1", "refund");

        assertThat(response.transactionType()).isEqualTo(TransactionType.REVERSAL);
    }

    @Test
    @DisplayName("reverse: stranger with no wallet in tx gets 403 and no reversal happens")
    void reverse_Stranger_ThrowsForbidden() {
        UUID origId = UUID.randomUUID();
        UUID srcId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID dstId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        LedgerTransaction original = LedgerTransaction.builder()
            .id(origId)
            .referenceId("P2P-9")
            .transactionType(TransactionType.P2P_TRANSFER)
            .amount(1000L)
            .currency("USD")
            .createdAt(Instant.now())
            .build();
        original.addEntry(LedgerEntry.builder()
            .walletId(srcId).entryType(EntryType.DEBIT)
            .amount(1000L).lineNo(1).balanceAfter(0L).build());
        original.addEntry(LedgerEntry.builder()
            .walletId(dstId).entryType(EntryType.CREDIT)
            .amount(1000L).lineNo(2).balanceAfter(1000L).build());

        when(callerProvider.currentUserIdOrNull()).thenReturn("STRANGER");
        when(walletService.getWalletOwnerId(any())).thenReturn("SOMEONE_ELSE");
        when(ledgerFacade.getTransaction(origId)).thenReturn(original);

        assertThatThrownBy(() -> transferService.reverse(origId, "IDEM-REV-X", "steal"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("does not involve current user");
        verify(ledgerFacade, never()).reverseWithRetry(any(), any(), any());
    }
}
