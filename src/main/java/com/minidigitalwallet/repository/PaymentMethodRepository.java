package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.PaymentMethod;
import com.minidigitalwallet.entity.enums.PaymentMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByOwnerIdAndStatus(String ownerId, PaymentMethodStatus status);

    List<PaymentMethod> findByOwnerId(String ownerId);

    Optional<PaymentMethod> findByIdAndStatus(UUID id, PaymentMethodStatus status);

    Optional<PaymentMethod> findByOwnerIdAndIsDefaultTrueAndStatus(String ownerId, PaymentMethodStatus status);

    @Modifying
    @Query("UPDATE PaymentMethod p SET p.isDefault = false WHERE p.ownerId = :ownerId AND p.id != :excludedId")
    void resetDefaultFlags(@Param("ownerId") String ownerId, @Param("excludedId") UUID excludedId);
}
