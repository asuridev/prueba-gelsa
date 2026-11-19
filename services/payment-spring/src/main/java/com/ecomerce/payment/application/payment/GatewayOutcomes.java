package com.ecomerce.payment.application.payment;

import com.ecomerce.payment.domain.payment.GatewayOutcome;

/**
 * Ajustes del desenlace que contestó la pasarela a una petición NUESTRA: la referencia es la del cobro que
 * estamos tratando (no la que la pasarela eche de menos en su respuesta), y el id de la pasarela, si no llegó,
 * es el que ya conocíamos.
 */
final class GatewayOutcomes {

    private GatewayOutcomes() {
    }

    static GatewayOutcome forPayment(GatewayOutcome outcome, String reference, String knownGatewayPaymentId) {
        return new GatewayOutcome(outcome.status(), reference,
                outcome.gatewayPaymentId() != null ? outcome.gatewayPaymentId() : knownGatewayPaymentId,
                outcome.failureReason(), outcome.customerAction(), outcome.refundedAmount());
    }
}
