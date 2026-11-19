package com.ecomerce.payment.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * La autorización quedó anulada, a petición del consumidor o porque la pasarela la expiró; no se cobrará.
 */
public record PaymentCanceledEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, Boolean requested) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentCanceledEvent of(UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, Boolean requested) {
        return new PaymentCanceledEvent(EventMetadata.now("PaymentCanceled"), paymentId, chargeRequestId, customerRef, amount, currency, requested);
    }
}
