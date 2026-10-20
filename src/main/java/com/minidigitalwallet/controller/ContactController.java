package com.minidigitalwallet.controller;

import com.minidigitalwallet.dto.request.CreateContactRequest;
import com.minidigitalwallet.dto.response.ContactResponse;
import com.minidigitalwallet.exception.AppException;
import com.minidigitalwallet.security.CurrentUser;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<List<ContactResponse>> getContacts() {
        String ownerId = requireCallerId();
        return ResponseEntity.ok(contactService.getContacts(ownerId));
    }

    @PostMapping
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody CreateContactRequest request) {
        String ownerId = requireCallerId();
        ContactResponse response = contactService.createContact(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable("id") UUID id) {
        String ownerId = requireCallerId();
        contactService.deleteContact(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<ContactResponse> toggleFavorite(@PathVariable("id") UUID id) {
        String ownerId = requireCallerId();
        return ResponseEntity.ok(contactService.toggleFavorite(ownerId, id));
    }

    private String requireCallerId() {
        UserPrincipal principal = CurrentUser.getOrNull();
        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED, "auth-required");
        }
        return principal.getId().toString();
    }
}
