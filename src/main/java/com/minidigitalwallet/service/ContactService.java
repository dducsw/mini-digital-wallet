package com.minidigitalwallet.service;

import com.minidigitalwallet.dto.request.CreateContactRequest;
import com.minidigitalwallet.dto.response.ContactResponse;

import java.util.List;
import java.util.UUID;

public interface ContactService {

    List<ContactResponse> getContacts(String ownerId);

    ContactResponse createContact(String ownerId, CreateContactRequest request);

    void deleteContact(String ownerId, UUID contactId);

    ContactResponse toggleFavorite(String ownerId, UUID contactId);
}
