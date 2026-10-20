package com.minidigitalwallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_contacts_owner_account", columnNames = {"owner_id", "account_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 50)
    private String ownerId;

    @Column(name = "account_number", nullable = false, length = 14)
    private String accountNumber;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "is_favorite", nullable = false)
    @Builder.Default
    private Boolean isFavorite = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isFavorite == null) {
            this.isFavorite = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
