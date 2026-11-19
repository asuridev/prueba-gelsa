package com.ecomerce.payment.infrastructure.persistence.entities;

import com.ecomerce.payment.domain.enums.FailureReason;
import com.ecomerce.payment.domain.enums.PaymentCancelOrigin;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payments_natural", columnNames = { "charge_request_id" }), indexes = { @Index(name = "idx_payments_status_awaiting_since", columnList = "status, awaiting_since"), @Index(name = "idx_payments_customer_ref_created_at", columnList = "customer_ref, created_at") })
public class PaymentJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "charge_request_id", nullable = false, length = 100)
    private String chargeRequestId;

    @Column(name = "customer_ref", nullable = false, length = 200)
    private String customerRef;

    @Column(name = "amount", nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "gateway_payment_id", length = 255)
    private String gatewayPaymentId;

    @Column(name = "awaiting_since")
    private Instant awaitingSince;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private FailureReason failureReason;

    @Column(name = "customer_action", columnDefinition = "text")
    private String customerAction;

    @Column(name = "action_required_since")
    private Instant actionRequiredSince;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_origin")
    private PaymentCancelOrigin cancelOrigin;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<RefundJpa> refunds = new ArrayList<>();

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getChargeRequestId() {
        return chargeRequestId;
    }

    public void setChargeRequestId(String chargeRequestId) {
        this.chargeRequestId = chargeRequestId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public void setCustomerRef(String customerRef) {
        this.customerRef = customerRef;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public Instant getAwaitingSince() {
        return awaitingSince;
    }

    public void setAwaitingSince(Instant awaitingSince) {
        this.awaitingSince = awaitingSince;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(FailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public String getCustomerAction() {
        return customerAction;
    }

    public void setCustomerAction(String customerAction) {
        this.customerAction = customerAction;
    }

    public Instant getActionRequiredSince() {
        return actionRequiredSince;
    }

    public void setActionRequiredSince(Instant actionRequiredSince) {
        this.actionRequiredSince = actionRequiredSince;
    }

    public PaymentCancelOrigin getCancelOrigin() {
        return cancelOrigin;
    }

    public void setCancelOrigin(PaymentCancelOrigin cancelOrigin) {
        this.cancelOrigin = cancelOrigin;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<RefundJpa> getRefunds() {
        return refunds;
    }

    public void setRefunds(List<RefundJpa> refunds) {
        this.refunds = refunds;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
