package com.ecomerce.payment.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * La pasarela capturó el cobro entero: está cobrado.
 */
public record PaymentCapturedEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentCapturedEvent of(UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency) {
        return new PaymentCapturedEvent(EventMetadata.now("PaymentCaptured"), paymentId, chargeRequestId, customerRef, amount, currency);
    }
}
