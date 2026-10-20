package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByOwnerIdOrderByIsFavoriteDescCreatedAtDesc(String ownerId);

    Optional<Contact> findByIdAndOwnerId(UUID id, String ownerId);

    boolean existsByOwnerIdAndAccountNumber(String ownerId, String accountNumber);
}
