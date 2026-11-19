package com.ecomerce.order.infrastructure.http;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body wire de payment.requestCharge (contrato del sistema externo).
 */
public record RequestChargeRequest(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId) {

    public RequestChargeRequest {
        if (chargeRequestId == null) {
            throw new IllegalStateException(
                    "payment.requestCharge: la petición no lleva 'chargeRequestId', que el contrato declara obligatorio");
        }
        if (customerRef == null) {
            throw new IllegalStateException(
                    "payment.requestCharge: la petición no lleva 'customerRef', que el contrato declara obligatorio");
        }
        if (amount == null) {
            throw new IllegalStateException(
                    "payment.requestCharge: la petición no lleva 'amount', que el contrato declara obligatorio");
        }
        if (currency == null) {
            throw new IllegalStateException(
                    "payment.requestCharge: la petición no lleva 'currency', que el contrato declara obligatorio");
        }
    }
}
