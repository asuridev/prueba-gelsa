package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentRefundFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    private static Map<String, Object> refundedData(String paymentId, String refundRequestId, double refundAmount,
            double refundedAmount, String status) {
        return exp("paymentId", paymentId, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "refundRequestId", refundRequestId, "refundAmount", refundAmount, "refundedAmount", refundedAmount,
                "currency", "EUR", "status", status);
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-005: devoluciones parciales hasta devolverlo todo")
    void partialRefundsUntilEverythingIsRefunded() {
        // Given: p1 (ch-001, 25.90 EUR) capturado.
        String p1 = seedCaptured("ch-001", "cus-1", "25.90");
        String gatewayPaymentId = jsonPath(readPayment(p1), "$.gatewayPaymentId");
        String reason = "Línea devuelta";

        // When: primera devolución parcial.
        gatewayRefunds("ch-001", new BigDecimal("10.00"));
        String body = "{\"refundRequestId\": \"rf-001\", \"amount\": 10.00, \"reason\": \"" + reason + "\"}";
        Response first = refund(p1, body);
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        String r1 = jsonPath(first, "$.refunds[0].id");
        assertIsUuid(r1);
        Map<String, Object> rf1 = exp("id", r1, "refundRequestId", "rf-001", "amount", 10.0, "reason", reason,
                "status", "succeeded", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p1);
        assertPayment(first, 200, payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "captured", "gatewayPaymentId", gatewayPaymentId,
                "refundedAmount", 10.0, "refunds", List.of(rf1)));
        assertEvent("PaymentRefunded", "ch-001", refundedData(p1, "rf-001", 10.0, 10.0, "captured"));

        // When: la misma petición, y otra vez sin amount.
        Map<String, Object> afterFirst = payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "captured", "gatewayPaymentId", gatewayPaymentId,
                "refundedAmount", 10.0, "refunds", List.of(rf1));
        assertPayment(refund(p1, body), 200, afterFirst);
        assertPayment(refund(p1, "{\"refundRequestId\": \"rf-001\"}"), 200, afterFirst);
        assertThat(refundCalls("ch-001")).as("una sola devolución para rf-001").isEqualTo(1);
        awaitEvents("PaymentRefunded", "ch-001", 1);

        // When: rf-001 con otro importe.
        assertError(refund(p1, "{\"refundRequestId\": \"rf-001\", \"amount\": 5.00}"), 409,
                "REFUND_REQUEST_CONFLICT");

        // When: rf-002 sin amount devuelve lo que queda.
        gatewayRefunds("ch-001", new BigDecimal("15.90"));
        Response rest = refund(p1, "{\"refundRequestId\": \"rf-002\"}");
        assertThat(rest.status()).as(rest.body()).isEqualTo(200);
        String r2 = jsonPath(rest, "$.refunds[1].id");
        assertIsUuid(r2);
        Map<String, Object> rf2 = exp("id", r2, "refundRequestId", "rf-002", "amount", 15.9, "reason", null,
                "status", "succeeded", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p1);
        assertPayment(rest, 200, payment("id", p1, "chargeRequestId", "ch-001", "customerRef", "cus-1",
                "amount", 25.9, "currency", "EUR", "status", "refunded", "gatewayPaymentId", gatewayPaymentId,
                "refundedAmount", 25.9, "refunds", List.of(rf1, rf2)));
        assertEvent("PaymentRefunded", "ch-001", "refundRequestId", "rf-002",
                refundedData(p1, "rf-002", 15.9, 25.9, "refunded"));

        // When: devolver más y capturar de nuevo.
        assertError(refund(p1, "{\"refundRequestId\": \"rf-003\", \"amount\": 1.00}"), 409,
                "PAYMENT_NOT_REFUNDABLE");
        Response recapture = capture(p1);
        assertThat(recapture.status()).as(recapture.body()).isEqualTo(200);
        String status = jsonPath(recapture, "$.status");
        assertThat(status).isEqualTo("refunded");
    }
}
