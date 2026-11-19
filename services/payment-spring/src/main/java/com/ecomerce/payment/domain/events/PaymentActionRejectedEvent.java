package com.ecomerce.payment.domain.events;

import com.ecomerce.payment.domain.enums.PaymentActionRejectedAction;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * La pasarela rechazó una captura, una anulación o una devolución; el cobro volvió al estado del que salió.
 */
public record PaymentActionRejectedEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, PaymentActionRejectedAction action, String refundRequestId, BigDecimal refundAmount, PaymentStatus status) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentActionRejectedEvent of(UUID paymentId, String chargeRequestId, String customerRef, PaymentActionRejectedAction action, String refundRequestId, BigDecimal refundAmount, PaymentStatus status) {
        return new PaymentActionRejectedEvent(EventMetadata.now("PaymentActionRejected"), paymentId, chargeRequestId, customerRef, action, refundRequestId, refundAmount, status);
    }
}
