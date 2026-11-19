package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeAuthorizedFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-001: un cobro con el cliente presente se autoriza")
    void chargeWithCustomerPresentIsAuthorized() {
        gatewayAuthorizes("ch-001");
        Response created = charge("ch-001", "cus-1", "25.90", "EUR", tokenSource());

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String p1 = idOf(created);
        assertThat(created.header("Location")).endsWith("/api/v1/payments/" + p1);
        String gatewayPaymentId = jsonPath(created, "$.gatewayPaymentId");
        assertThat(gatewayPaymentId).isNotBlank();

        java.util.Map<String, Object> expected = payment("chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId);
        assertPayment(created, 201, expected);
        assertThat(created.body()).doesNotContain("tok_");

        assertPayment(readPayment(p1), 200, payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId));
        assertPayment(readByCharge("ch-001"), 200, payment("id", p1, "chargeRequestId", "ch-001",
                "customerRef", "cus-1", "amount", 25.9, "currency", "EUR", "status", "authorized",
                "gatewayPaymentId", gatewayPaymentId));

        assertEvent("PaymentAuthorized", "ch-001", eventData(p1, "ch-001", "cus-1", 25.9,
                "gatewayPaymentId", gatewayPaymentId));
    }
}
