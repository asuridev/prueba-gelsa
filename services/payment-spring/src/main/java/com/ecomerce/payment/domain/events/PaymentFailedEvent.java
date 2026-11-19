package com.ecomerce.payment.domain.events;

import com.ecomerce.payment.domain.enums.FailureReason;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * El cobro falló; failureReason usa el vocabulario neutro (notReceived si la pasarela nunca lo conoció).
 */
public record PaymentFailedEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, FailureReason failureReason) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentFailedEvent of(UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, FailureReason failureReason) {
        return new PaymentFailedEvent(EventMetadata.now("PaymentFailed"), paymentId, chargeRequestId, customerRef, amount, currency, failureReason);
    }
}
