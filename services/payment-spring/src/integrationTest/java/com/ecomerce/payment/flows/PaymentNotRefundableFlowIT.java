package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentNotRefundableFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-006: lo que no se puede devolver")
    void whatCannotBeRefunded() {
        // Given: ch-090 capturado (15.00), ch-091 capturado y ch-092 autorizado.
        String p90 = seedCaptured("ch-090", "cus-1", "15.00");
        String p91 = seedCaptured("ch-091");
        String p92 = seedAuthorized("ch-092");

        // When: sobre ch-090, importe excesivo, mal escalado, y las dos cosas a la vez.
        assertError(refund(p90, "{\"refundRequestId\": \"rf-010\", \"amount\": 20.00}"), 422,
                "REFUND_AMOUNT_EXCEEDED");
        assertThat(refundCalls("ch-090")).as("la pasarela no recibe nada").isZero();
        assertError(refund(p90, "{\"refundRequestId\": \"rf-011\", \"amount\": 1.005}"), 400,
                "AMOUNT_SCALE_INVALID");
        assertError(refund(p90, "{\"refundRequestId\": \"rf-012\", \"amount\": 20.005}"), 400,
                "AMOUNT_SCALE_INVALID");
        assertThat(refundCalls("ch-090")).isZero();

        // When: la pasarela rechaza la devolución de ch-091.
        gatewayRejects(GatewayCall.REFUND);
        gatewayReports("ch-091", "CAPTURED");
        String rejectedBody = "{\"refundRequestId\": \"rf-013\", \"amount\": 5.00}";
        assertError(refund(p91, rejectedBody), 422, "REFUND_REJECTED");

        Map<String, Object> failedRefund = exp("id", NOT_NULL, "refundRequestId", "rf-013", "amount", 5.0,
                "reason", null, "status", "failed", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p91);
        Map<String, Object> expected = payment("id", p91, "chargeRequestId", "ch-091", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "captured", "gatewayPaymentId", NOT_NULL,
                "refundedAmount", 0, "refunds", List.of(failedRefund));
        assertPayment(readPayment(p91), 200, expected);
        assertEvent("PaymentActionRejected", "ch-091",
                rejectedData(p91, "ch-091", "refund", "rf-013", 5.0, "captured"));

        // Repetir rf-013: el cobro tal cual y ninguna devolución más a la pasarela.
        long callsBefore = refundCalls("ch-091");
        assertPayment(refund(p91, rejectedBody), 200, expected);
        assertThat(refundCalls("ch-091")).isEqualTo(callsBefore);

        // When: autorizado, inexistente y credencial sin payment:refund.
        assertError(refund(p92, "{\"refundRequestId\": \"rf-014\", \"amount\": 1.00}"), 409,
                "PAYMENT_NOT_REFUNDABLE");
        assertError(refund(UUID.randomUUID().toString(), "{\"refundRequestId\": \"rf-015\", \"amount\": 1.00}"), 404,
                "PAYMENT_NOT_FOUND");
        Response forbidden = post(ROUTE_BASE + "/payments/" + p90 + "/refunds",
                "{\"refundRequestId\": \"rf-016\", \"amount\": 1.00}", checkoutToken());
        assertThat(forbidden.status()).as(forbidden.body()).isEqualTo(403);
    }
}
