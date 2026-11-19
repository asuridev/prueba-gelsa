package com.ecomerce.payment.domain.events;

import com.ecomerce.payment.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * La pasarela completó una devolución. status dice si el cobro quedó devuelto entero (refunded) o en parte (captured).
 */
public record PaymentRefundedEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, String refundRequestId, BigDecimal refundAmount, BigDecimal refundedAmount, String currency, PaymentStatus status) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentRefundedEvent of(UUID paymentId, String chargeRequestId, String customerRef, String refundRequestId, BigDecimal refundAmount, BigDecimal refundedAmount, String currency, PaymentStatus status) {
        return new PaymentRefundedEvent(EventMetadata.now("PaymentRefunded"), paymentId, chargeRequestId, customerRef, refundRequestId, refundAmount, refundedAmount, currency, status);
    }
}
