package com.ecomerce.notifications.infrastructure.persistence.entities;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "sender_settingses", uniqueConstraints = @UniqueConstraint(name = "uk_sender_settingses_natural", columnNames = { "`key`" }))
public class SenderSettingsJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "`key`")
    private SenderSettingsKey key;

    @Column(name = "sender_address", nullable = false, length = 254)
    private String senderAddress;

    @Column(name = "sender_name", length = 120)
    private String senderName;

    @Column(name = "reply_to_address", length = 254)
    private String replyToAddress;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SenderSettingsKey getKey() {
        return key;
    }

    public void setKey(SenderSettingsKey key) {
        this.key = key;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReplyToAddress() {
        return replyToAddress;
    }

    public void setReplyToAddress(String replyToAddress) {
        this.replyToAddress = replyToAddress;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
