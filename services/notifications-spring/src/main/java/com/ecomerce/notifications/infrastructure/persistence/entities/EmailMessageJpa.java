package com.ecomerce.notifications.infrastructure.persistence.entities;

import com.ecomerce.notifications.domain.enums.FailureReason;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "email_messages", uniqueConstraints = @UniqueConstraint(name = "uk_email_messages_natural", columnNames = { "requested_by", "idempotency_key" }), indexes = { @Index(name = "idx_email_messages_status_sending_since", columnList = "status, sending_since"), @Index(name = "idx_email_messages_status_requested_at", columnList = "status, requested_at"), @Index(name = "idx_email_messages_requested_at", columnList = "requested_at"), @Index(name = "idx_email_messages_template_code_requested_at", columnList = "template_code, requested_at"), @Index(name = "idx_email_messages_requested_by_requested_at", columnList = "requested_by, requested_at"), @Index(name = "idx_email_messages_personal_data_purged_at_requested_at", columnList = "personal_data_purged_at, requested_at") })
public class EmailMessageJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @ElementCollection
    @CollectionTable(name = "email_message_recipients", joinColumns = @JoinColumn(name = "email_message_id", foreignKey = @ForeignKey(name = "fk_email_message_recipients_email_message")), indexes = { @Index(name = "idx_email_messages_recipients", columnList = "recipients, email_message_id"), @Index(name = "ix_email_message_recipients_email_message_id", columnList = "email_message_id") })
    @OrderColumn(name = "recipients_order")
    @BatchSize(size = 100)
    @Column(name = "recipients", length = 254)
    private List<String> recipients = new ArrayList<>();

    @Column(name = "rendered_subject", length = 300)
    private String renderedSubject;

    @ElementCollection
    @CollectionTable(name = "email_message_variable_values", joinColumns = @JoinColumn(name = "email_message_id", foreignKey = @ForeignKey(name = "fk_email_message_variable_values_email_message")), indexes = @Index(name = "ix_email_message_variable_values_email_message_id", columnList = "email_message_id"))
    @OrderColumn(name = "variable_values_order")
    @BatchSize(size = 100)
    private List<VariableValueJpa> variableValues = new ArrayList<>();

    // SenderSnapshot.address aplanado.
    @Column(name = "sender_address", nullable = false, length = 254)
    private String senderAddress;

    // SenderSnapshot.name aplanado.
    @Column(name = "sender_name", length = 120)
    private String senderName;

    // SenderSnapshot.replyToAddress aplanado.
    @Column(name = "sender_reply_to_address", length = 254)
    private String senderReplyToAddress;

    @Column(name = "template_code", nullable = false, columnDefinition = "varchar(255) collate \"C\"")
    private String templateCode;

    @Column(name = "template_version_number", nullable = false)
    private Integer templateVersionNumber;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MessageStatus status;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "sending_since")
    private Instant sendingSince;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private FailureReason failureReason;

    @Column(name = "personal_data_purged_at")
    private Instant personalDataPurgedAt;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public void setRenderedSubject(String renderedSubject) {
        this.renderedSubject = renderedSubject;
    }

    public List<VariableValueJpa> getVariableValues() {
        return variableValues;
    }

    public void setVariableValues(List<VariableValueJpa> variableValues) {
        this.variableValues = variableValues;
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

    public String getSenderReplyToAddress() {
        return senderReplyToAddress;
    }

    public void setSenderReplyToAddress(String senderReplyToAddress) {
        this.senderReplyToAddress = senderReplyToAddress;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Integer getTemplateVersionNumber() {
        return templateVersionNumber;
    }

    public void setTemplateVersionNumber(Integer templateVersionNumber) {
        this.templateVersionNumber = templateVersionNumber;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(UUID templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getSendingSince() {
        return sendingSince;
    }

    public void setSendingSince(Instant sendingSince) {
        this.sendingSince = sendingSince;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(FailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getPersonalDataPurgedAt() {
        return personalDataPurgedAt;
    }

    public void setPersonalDataPurgedAt(Instant personalDataPurgedAt) {
        this.personalDataPurgedAt = personalDataPurgedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
