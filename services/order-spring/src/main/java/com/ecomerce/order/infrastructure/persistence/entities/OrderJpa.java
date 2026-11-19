package com.ecomerce.order.infrastructure.persistence.entities;

import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "orders", indexes = { @Index(name = "idx_orders_customer_ref_created_at", columnList = "customer_ref, created_at"), @Index(name = "idx_orders_status_charge_order_awaiting_since", columnList = "status, charge_order_awaiting_since"), @Index(name = "idx_orders_status_capture_order_payment_awaiting_since", columnList = "status, capture_order_payment_awaiting_since"), @Index(name = "idx_orders_status_cancel_order_payment_awaiting_since", columnList = "status, cancel_order_payment_awaiting_since"), @Index(name = "idx_orders_status_action_required_since", columnList = "status, action_required_since") })
public class OrderJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_ref", nullable = false, length = 200)
    private String customerRef;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_amount", precision = 19, scale = 3)
    private BigDecimal totalAmount;

    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "customer_action", columnDefinition = "text")
    private String customerAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private PaymentFailureReason failureReason;

    @Column(name = "charge_order_awaiting_since")
    private Instant chargeOrderAwaitingSince;

    @Column(name = "capture_order_payment_awaiting_since")
    private Instant captureOrderPaymentAwaitingSince;

    @Column(name = "action_required_since")
    private Instant actionRequiredSince;

    @Column(name = "cancel_order_payment_awaiting_since")
    private Instant cancelOrderPaymentAwaitingSince;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_order_lines_order"))
    @BatchSize(size = 100)
    private List<OrderLineJpa> lines = new ArrayList<>();

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public void setCustomerRef(String customerRef) {
        this.customerRef = customerRef;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getCustomerAction() {
        return customerAction;
    }

    public void setCustomerAction(String customerAction) {
        this.customerAction = customerAction;
    }

    public PaymentFailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(PaymentFailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getChargeOrderAwaitingSince() {
        return chargeOrderAwaitingSince;
    }

    public void setChargeOrderAwaitingSince(Instant chargeOrderAwaitingSince) {
        this.chargeOrderAwaitingSince = chargeOrderAwaitingSince;
    }

    public Instant getCaptureOrderPaymentAwaitingSince() {
        return captureOrderPaymentAwaitingSince;
    }

    public void setCaptureOrderPaymentAwaitingSince(Instant captureOrderPaymentAwaitingSince) {
        this.captureOrderPaymentAwaitingSince = captureOrderPaymentAwaitingSince;
    }

    public Instant getActionRequiredSince() {
        return actionRequiredSince;
    }

    public void setActionRequiredSince(Instant actionRequiredSince) {
        this.actionRequiredSince = actionRequiredSince;
    }

    public Instant getCancelOrderPaymentAwaitingSince() {
        return cancelOrderPaymentAwaitingSince;
    }

    public void setCancelOrderPaymentAwaitingSince(Instant cancelOrderPaymentAwaitingSince) {
        this.cancelOrderPaymentAwaitingSince = cancelOrderPaymentAwaitingSince;
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

    public List<OrderLineJpa> getLines() {
        return lines;
    }

    public void setLines(List<OrderLineJpa> lines) {
        this.lines = lines;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
