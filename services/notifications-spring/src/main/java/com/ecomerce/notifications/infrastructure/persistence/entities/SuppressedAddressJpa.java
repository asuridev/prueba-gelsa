package com.ecomerce.notifications.infrastructure.persistence.entities;

import com.ecomerce.notifications.domain.enums.SuppressionReason;
import com.ecomerce.notifications.domain.enums.SuppressionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppressed_addresses", uniqueConstraints = @UniqueConstraint(name = "uk_suppressed_addresses_natural", columnNames = { "address_normalized" }), indexes = { @Index(name = "idx_suppressed_addresses_status_suppressed_at", columnList = "status, suppressed_at") })
public class SuppressedAddressJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "address", nullable = false, length = 254)
    private String address;

    // address plegado (compare: ignore-case): lo estampa el adaptador con TextFold al guardar.
    @Column(name = "address_normalized", nullable = false, length = 254)
    private String addressNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private SuppressionReason reason;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SuppressionStatus status;

    @Column(name = "suppressed_at", nullable = false)
    private Instant suppressedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddressNormalized() {
        return addressNormalized;
    }

    public void setAddressNormalized(String addressNormalized) {
        this.addressNormalized = addressNormalized;
    }

    public SuppressionReason getReason() {
        return reason;
    }

    public void setReason(SuppressionReason reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public SuppressionStatus getStatus() {
        return status;
    }

    public void setStatus(SuppressionStatus status) {
        this.status = status;
    }

    public Instant getSuppressedAt() {
        return suppressedAt;
    }

    public void setSuppressedAt(Instant suppressedAt) {
        this.suppressedAt = suppressedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
