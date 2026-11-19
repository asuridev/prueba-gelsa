package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentCaptureFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-001: se captura un cobro autorizado, y la captura repetida")
    void capturesAnAuthorizedPaymentAndRepeats() {
        // Given: p1 (ch-001, 25.90 EUR) autorizado.
        String p1 = seedAuthorized("ch-001", "cus-1", "25.90");
        Response read = readPayment(p1);
        String gatewayPaymentId = jsonPath(read, "$.gatewayPaymentId");

        // When: se captura.
        gatewayCaptures("ch-001");
        Response captured = capture(p1);
        assertPayment(captured, 200, payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "captured", "gatewayPaymentId", gatewayPaymentId));
        assertEvent("PaymentCaptured", "ch-001", eventData(p1, "ch-001", "cus-1", 25.9));

        // When: se repite: el cobro tal cual, una sola captura en la pasarela y ningún evento más.
        Response repeated = capture(p1);
        assertPayment(repeated, 200, payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "captured", "gatewayPaymentId", gatewayPaymentId));
        assertThat(captureCalls("ch-001")).isEqualTo(1);
        awaitEvents("PaymentCaptured", "ch-001", 1);
    }
}
