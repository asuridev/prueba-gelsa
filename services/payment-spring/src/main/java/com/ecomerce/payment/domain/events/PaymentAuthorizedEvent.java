package com.ecomerce.payment.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * La pasarela autorizó el cobro: el importe está retenido y se puede capturar o anular.
 */
public record PaymentAuthorizedEvent(EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, String gatewayPaymentId) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static PaymentAuthorizedEvent of(UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, String gatewayPaymentId) {
        return new PaymentAuthorizedEvent(EventMetadata.now("PaymentAuthorized"), paymentId, chargeRequestId, customerRef, amount, currency, gatewayPaymentId);
    }
}
