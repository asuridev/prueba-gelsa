package com.ecomerce.payment.infrastructure.persistence.entities;

import com.ecomerce.payment.domain.enums.PaymentMethodStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "payment_methods", indexes = { @Index(name = "idx_payment_methods_wallet_status", columnList = "wallet_id, status") })
public class PaymentMethodJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gateway_method_ref", nullable = false, length = 255)
    private String gatewayMethodRef;

    // CardDetails.brand aplanado.
    @Column(name = "card_brand", nullable = false, length = 30)
    private String cardBrand;

    // CardDetails.last4 aplanado.
    @Column(name = "card_last4", nullable = false)
    private String cardLast4;

    // CardDetails.expMonth aplanado.
    @Column(name = "card_exp_month", nullable = false)
    private Integer cardExpMonth;

    // CardDetails.expYear aplanado.
    @Column(name = "card_exp_year", nullable = false)
    private Integer cardExpYear;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentMethodStatus status;

    @Column(name = "expired")
    private Boolean expired;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_methods_wallet"))
    private WalletJpa wallet;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getGatewayMethodRef() {
        return gatewayMethodRef;
    }

    public void setGatewayMethodRef(String gatewayMethodRef) {
        this.gatewayMethodRef = gatewayMethodRef;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public Integer getCardExpMonth() {
        return cardExpMonth;
    }

    public void setCardExpMonth(Integer cardExpMonth) {
        this.cardExpMonth = cardExpMonth;
    }

    public Integer getCardExpYear() {
        return cardExpYear;
    }

    public void setCardExpYear(Integer cardExpYear) {
        this.cardExpYear = cardExpYear;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public PaymentMethodStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentMethodStatus status) {
        this.status = status;
    }

    public Boolean getExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }

    public WalletJpa getWallet() {
        return wallet;
    }

    public void setWallet(WalletJpa wallet) {
        this.wallet = wallet;
    }
}
