package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeWithSavedMethodFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-007: un cobro con un medio guardado")
    void chargeWithSavedMethod() {
        String m1 = seedMethod("cus-1");

        gatewayAuthorizes("ch-050");
        Response created = charge("ch-050", "cus-1", "30.00", "EUR", methodSource(m1));

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String p50 = idOf(created);
        String gatewayPaymentId = jsonPath(created, "$.gatewayPaymentId");
        assertPayment(created, 201, payment("chargeRequestId", "ch-050", "customerRef", "cus-1", "amount", 30.0,
                "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId,
                "paymentMethodId", m1));
        assertEvent("PaymentAuthorized", "ch-050", eventData(p50, "ch-050", "cus-1", 30.0,
                "gatewayPaymentId", gatewayPaymentId));
    }
}
