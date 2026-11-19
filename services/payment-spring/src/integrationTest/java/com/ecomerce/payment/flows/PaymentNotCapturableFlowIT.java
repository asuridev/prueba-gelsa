package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentNotCapturableFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-002: lo que no se puede capturar")
    void whatCannotBeCaptured() {
        // Given: ch-060 en pending, ch-061 y ch-062 autorizados.
        String p61 = seedAuthorized("ch-061");
        String p62 = seedAuthorized("ch-062");
        String p60 = seedPending("ch-060");

        // When/Then: pending no admite captura.
        assertError(capture(p60), 409, "PAYMENT_NOT_CAPTURABLE");

        // El inexistente.
        assertError(capture(UUID.randomUUID().toString()), 404, "PAYMENT_NOT_FOUND");

        // ch-061: la pasarela da la autorización por caducada.
        gatewayExpiresAuthorization("ch-061");
        assertError(capture(p61), 409, "AUTHORIZATION_EXPIRED");
        Response expired = readPayment(p61);
        String expiredStatus = jsonPath(expired, "$.status");
        assertThat(expiredStatus).isEqualTo("canceled");
        assertPayment(expired, 200, payment("id", p61, "chargeRequestId", "ch-061", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "canceled", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentCanceled", "ch-061", eventData(p61, "ch-061", "cus-1", 10.0, "requested", false));

        // ch-062: la pasarela rechaza la captura y, preguntada, sigue autorizado.
        gatewayRejects(GatewayCall.CAPTURE);
        gatewayReports("ch-062", "AUTHORIZED");
        assertError(capture(p62), 422, "CAPTURE_REJECTED");
        assertPayment(readPayment(p62), 200, payment("id", p62, "chargeRequestId", "ch-062", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentActionRejected", "ch-062", rejectedData(p62, "ch-062", "capture", null, null, "authorized"));

        // Repetir sobre ch-061: ya está canceled.
        assertError(capture(p61), 409, "PAYMENT_NOT_CAPTURABLE");
    }
}
