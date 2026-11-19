package com.ecomerce.payment.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El cobro necesita que el cliente complete una acción (3DS). customerAction es opaca y la consume el componente de la pasarela en el navegador.
 */
public record PaymentActionRequiredEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, String customerAction) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentActionRequiredEvent of(UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, String customerAction) {
        return new PaymentActionRequiredEvent(EventMetadata.now("PaymentActionRequired"), paymentId, chargeRequestId, customerRef, amount, currency, customerAction);
    }
}
