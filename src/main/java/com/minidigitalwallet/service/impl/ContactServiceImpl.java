package com.minidigitalwallet.service.impl;

import com.minidigitalwallet.dto.request.CreateContactRequest;
import com.minidigitalwallet.dto.response.ContactResponse;
import com.minidigitalwallet.entity.Contact;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.exception.DuplicateRequestException;
import com.minidigitalwallet.repository.ContactRepository;
import com.minidigitalwallet.service.ContactService;
import com.minidigitalwallet.service.WalletLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;
    private final WalletLookup walletLookup;

    @Override
    @Transactional(readOnly = true)
    public List<ContactResponse> getContacts(String ownerId) {
        return contactRepository.findByOwnerIdOrderByIsFavoriteDescCreatedAtDesc(ownerId)
            .stream()
            .map(ContactResponse::from)
            .toList();
    }

    @Override
    @Transactional
    public ContactResponse createContact(String ownerId, CreateContactRequest request) {
        String cleanAcct = request.accountNumber().replaceAll("\\s+", "");

        // 1. Validate account number exists
        UUID targetWalletId = walletLookup.resolveWalletIdByAccountNumber(cleanAcct);
        if (targetWalletId == null) {
            throw new AppException("Account number not found: " + cleanAcct, HttpStatus.NOT_FOUND, "account-not-found");
        }

        // 2. Prevent adding self
        String targetOwnerId = walletLookup.getWalletOwnerId(targetWalletId);
        if (ownerId.equals(targetOwnerId)) {
            throw new AppException("Cannot add your own account to contacts", HttpStatus.BAD_REQUEST, "cannot-add-self");
        }

        // 3. Prevent duplicate
        if (contactRepository.existsByOwnerIdAndAccountNumber(ownerId, cleanAcct)) {
            throw new DuplicateRequestException("Account number already exists in your contacts: " + cleanAcct);
        }

        Contact contact = Contact.builder()
            .ownerId(ownerId)
            .accountNumber(cleanAcct)
            .nickname(request.nickname().trim())
            .isFavorite(false)
            .build();

        Contact saved = contactRepository.save(contact);
        log.info("Saved contact {} ({}) for owner {}", saved.getNickname(), cleanAcct, ownerId);
        return ContactResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteContact(String ownerId, UUID contactId) {
        Contact contact = contactRepository.findByIdAndOwnerId(contactId, ownerId)
            .orElseThrow(() -> new AppException("Contact not found: " + contactId, HttpStatus.NOT_FOUND, "contact-not-found"));
        contactRepository.delete(contact);
        log.info("Deleted contact {} for owner {}", contactId, ownerId);
    }

    @Override
    @Transactional
    public ContactResponse toggleFavorite(String ownerId, UUID contactId) {
        Contact contact = contactRepository.findByIdAndOwnerId(contactId, ownerId)
            .orElseThrow(() -> new AppException("Contact not found: " + contactId, HttpStatus.NOT_FOUND, "contact-not-found"));
        contact.setIsFavorite(!Boolean.TRUE.equals(contact.getIsFavorite()));
        Contact saved = contactRepository.save(contact);
        return ContactResponse.from(saved);
    }
}
