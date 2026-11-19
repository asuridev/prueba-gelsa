package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentCancelFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-003: se anula una autorización")
    void cancelsAnAuthorization() {
        // Given: ch-070 (20.00) autorizado, ch-072 capturado y ch-071 autorizado.
        String p70 = seedAuthorized("ch-070", "cus-1", "20.00");
        String p72 = seedCaptured("ch-072");
        String p71 = seedAuthorized("ch-071");
        String gatewayPaymentId = jsonPath(readPayment(p70), "$.gatewayPaymentId");

        // When: se anula ch-070, dos veces.
        gatewayCancels("ch-070");
        Response canceled = cancel(p70);
        assertPayment(canceled, 200, payment("id", p70, "chargeRequestId", "ch-070", "customerRef", "cus-1",
                "amount", 20.0, "currency", "EUR", "status", "canceled", "gatewayPaymentId", gatewayPaymentId));
        assertEvent("PaymentCanceled", "ch-070", eventData(p70, "ch-070", "cus-1", 20.0, "requested", true));

        Response again = cancel(p70);
        assertPayment(again, 200, payment("id", p70, "chargeRequestId", "ch-070", "customerRef", "cus-1",
                "amount", 20.0, "currency", "EUR", "status", "canceled", "gatewayPaymentId", gatewayPaymentId));
        assertThat(cancelCalls("ch-070")).as("la pasarela recibe una sola anulación").isEqualTo(1);
        awaitEvents("PaymentCanceled", "ch-070", 1);

        // When: la pasarela rechaza la anulación de ch-071.
        gatewayRejects(GatewayCall.CANCEL);
        gatewayReports("ch-071", "AUTHORIZED");
        assertError(cancel(p71), 422, "CANCEL_REJECTED");
        assertPayment(readPayment(p71), 200, payment("id", p71, "chargeRequestId", "ch-071", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentActionRejected", "ch-071",
                rejectedData(p71, "ch-071", "cancel", null, null, "authorized"));

        // El capturado y el inexistente.
        assertError(cancel(p72), 409, "PAYMENT_NOT_CANCELABLE");
        assertError(cancel(UUID.randomUUID().toString()), 404, "PAYMENT_NOT_FOUND");
    }
}
