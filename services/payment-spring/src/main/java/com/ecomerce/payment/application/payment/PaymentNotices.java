package com.ecomerce.payment.application.payment;

import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import org.springframework.stereotype.Component;

/**
 * Lo que se hace con un aviso de la pasarela YA VERIFICADO: preguntarle el estado del cobro y aplicar
 * el desenlace. El contenido del aviso no se usa para nada más que para saber de qué cobro habla.
 */
@Component
public class PaymentNotices {

    private final PaymentGateway gateway;
    private final PaymentOutcomeApplier applier;

    public PaymentNotices(PaymentGateway gateway, PaymentOutcomeApplier applier) {
        this.gateway = gateway;
        this.applier = applier;
    }

    public void onNotice(String gatewayPaymentId) {
        GatewayOutcome outcome = gateway.status(null, gatewayPaymentId);
        if (outcome.status() == GatewayStatus.PENDING || outcome.status() == GatewayStatus.NOT_FOUND) {
            return;
        }
        applier.apply(outcome);
    }
}
