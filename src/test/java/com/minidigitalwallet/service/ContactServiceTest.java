package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateContactRequest;
import com.minidigitalwallet.dto.response.ContactResponse;
import com.minidigitalwallet.entity.Contact;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.repository.ContactRepository;
import com.minidigitalwallet.service.impl.ContactServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private WalletLookup walletLookup;

    @InjectMocks
    private ContactServiceImpl contactService;

    private final String ownerId = "USR_ALICE";
    private final String targetOwnerId = "USR_BOB";
    private final String targetAccount = "123456789012";
    private final UUID targetWalletId = UUID.randomUUID();

    @Test
    @DisplayName("getContacts: returns list of contacts for owner")
    void getContacts_ReturnsList() {
        Contact contact = Contact.builder()
            .id(UUID.randomUUID())
            .ownerId(ownerId)
            .accountNumber(targetAccount)
            .nickname("Bob")
            .isFavorite(true)
            .build();

        when(contactRepository.findByOwnerIdOrderByIsFavoriteDescCreatedAtDesc(ownerId))
            .thenReturn(List.of(contact));

        List<ContactResponse> result = contactService.getContacts(ownerId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).nickname()).isEqualTo("Bob");
        assertThat(result.get(0).isFavorite()).isTrue();
    }

    @Test
    @DisplayName("createContact: successfully saves and returns contact")
    void createContact_Success() {
        CreateContactRequest request = new CreateContactRequest("1234 5678 9012", "Bob");

        when(walletLookup.resolveWalletIdByAccountNumber(targetAccount)).thenReturn(targetWalletId);
        when(walletLookup.getWalletOwnerId(targetWalletId)).thenReturn(targetOwnerId);
        when(contactRepository.existsByOwnerIdAndAccountNumber(ownerId, targetAccount)).thenReturn(false);
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> {
            Contact c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ContactResponse response = contactService.createContact(ownerId, request);
        assertThat(response.accountNumber()).isEqualTo(targetAccount);
        assertThat(response.nickname()).isEqualTo("Bob");
        assertThat(response.isFavorite()).isFalse();
    }

    @Test
    @DisplayName("createContact: throws 404 when target account does not exist")
    void createContact_NonExistentAccount_ThrowsNotFound() {
        CreateContactRequest request = new CreateContactRequest(targetAccount, "Bob");
        when(walletLookup.resolveWalletIdByAccountNumber(targetAccount)).thenReturn(null);

        assertThatThrownBy(() -> contactService.createContact(ownerId, request))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(appEx.getErrorCode()).isEqualTo("account-not-found");
            });
    }

    @Test
    @DisplayName("createContact: throws 400 when attempting to add self")
    void createContact_SelfAccount_ThrowsBadRequest() {
        CreateContactRequest request = new CreateContactRequest(targetAccount, "Myself");
        when(walletLookup.resolveWalletIdByAccountNumber(targetAccount)).thenReturn(targetWalletId);
        when(walletLookup.getWalletOwnerId(targetWalletId)).thenReturn(ownerId); // Same owner

        assertThatThrownBy(() -> contactService.createContact(ownerId, request))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> {
                AppException appEx = (AppException) ex;
                assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(appEx.getErrorCode()).isEqualTo("cannot-add-self");
            });
    }

    @Test
    @DisplayName("createContact: throws DuplicateRequestException when account already in contacts")
    void createContact_DuplicateAccount_ThrowsDuplicate() {
        CreateContactRequest request = new CreateContactRequest(targetAccount, "Bob");
        when(walletLookup.resolveWalletIdByAccountNumber(targetAccount)).thenReturn(targetWalletId);
        when(walletLookup.getWalletOwnerId(targetWalletId)).thenReturn(targetOwnerId);
        when(contactRepository.existsByOwnerIdAndAccountNumber(ownerId, targetAccount)).thenReturn(true);

        assertThatThrownBy(() -> contactService.createContact(ownerId, request))
            .isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    @DisplayName("deleteContact: removes existing contact")
    void deleteContact_Success() {
        UUID contactId = UUID.randomUUID();
        Contact contact = Contact.builder().id(contactId).ownerId(ownerId).build();
        when(contactRepository.findByIdAndOwnerId(contactId, ownerId)).thenReturn(Optional.of(contact));

        contactService.deleteContact(ownerId, contactId);
        verify(contactRepository).delete(contact);
    }

    @Test
    @DisplayName("toggleFavorite: flips isFavorite boolean")
    void toggleFavorite_Success() {
        UUID contactId = UUID.randomUUID();
        Contact contact = Contact.builder()
            .id(contactId)
            .ownerId(ownerId)
            .accountNumber(targetAccount)
            .nickname("Bob")
            .isFavorite(false)
            .build();

        when(contactRepository.findByIdAndOwnerId(contactId, ownerId)).thenReturn(Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        ContactResponse response = contactService.toggleFavorite(ownerId, contactId);
        assertThat(response.isFavorite()).isTrue();
    }
}
