package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class LostActionResponsesFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    /** La pasarela da el cobro por devuelto con ese total acumulado (latest_charge.amount_refunded, en céntimos). */
    private static void gatewayReportsRefundedTotal(String ref, String totalMinorUnits) {
        String id = gatewayIdFor(ref);
        String object = "{\"id\": \"" + id + "\", \"object\": \"payment_intent\", \"currency\": \"eur\", "
                + "\"status\": \"succeeded\", \"metadata\": {\"keel_reference\": \"" + ref + "\"}, "
                + "\"client_secret\": \"" + id + "_secret_test\", \"latest_charge\": {\"amount_refunded\": "
                + totalMinorUnits + ", \"currency\": \"eur\"}}";
        stubFor("GET", "/v1/payment_intents/" + id, 200, object);
    }

    private static Map<String, Object> refundedData(String paymentId, String ref, String refundRequestId,
            double refundAmount, double refundedAmount, String status) {
        return exp("paymentId", paymentId, "chargeRequestId", ref, "customerRef", "cus-1",
                "refundRequestId", refundRequestId, "refundAmount", refundAmount, "refundedAmount", refundedAmount,
                "currency", "EUR", "status", status);
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-002: acciones cuya respuesta se pierde, o que la pasarela nunca recibió")
    void actionsWhoseResponseIsLost() {
        // ── Captura sin respuesta, pero hecha ────────────────────────────────
        String p310 = seedAuthorized("ch-310");
        gatewayDoesNotAnswer(GatewayCall.CAPTURE);
        Response capturing = capture(p310);
        assertThat(capturing.status()).as(capturing.body()).isEqualTo(200);
        assertPayment(capturing, 200, payment("id", p310, "chargeRequestId", "ch-310", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "capturing", "gatewayPaymentId", NOT_NULL,
                "awaitingSince", NOT_NULL));
        Response repeated = capture(p310);
        assertThat(repeated.status()).as(repeated.body()).isEqualTo(200);
        String repeatedStatus = jsonPath(repeated, "$.status");
        assertThat(repeatedStatus).isEqualTo("capturing");
        assertThat(captureCalls("ch-310")).as("no se repite la captura").isEqualTo(1);

        gatewayReports("ch-310", "CAPTURED");
        ageAwaiting(p310);
        runSweep();
        awaitStatus(p310, "captured");
        assertPayment(readPayment(p310), 200, payment("id", p310, "chargeRequestId", "ch-310", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "captured", "gatewayPaymentId", NOT_NULL));
        awaitEvents("PaymentCaptured", "ch-310", 1);

        // ── Anulación que la pasarela no llegó a recibir ─────────────────────
        String p311 = seedAuthorized("ch-311");
        gatewayDoesNotAnswer(GatewayCall.CANCEL);
        Response canceling = cancel(p311);
        assertThat(canceling.status()).as(canceling.body()).isEqualTo(200);
        String cancelingStatus = jsonPath(canceling, "$.status");
        assertThat(cancelingStatus).isEqualTo("canceling");
        gatewayReports("ch-311", "AUTHORIZED");
        ageAwaiting(p311);
        runSweep();
        awaitStatus(p311, "authorized");
        assertPayment(readPayment(p311), 200, payment("id", p311, "chargeRequestId", "ch-311", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentActionRejected", "ch-311",
                rejectedData(p311, "ch-311", "cancel", null, null, "authorized"));
        // Se puede volver a anular.
        gatewayCancels("ch-311");
        Response again = cancel(p311);
        assertThat(again.status()).as(again.body()).isEqualTo(200);
        String againStatus = jsonPath(again, "$.status");
        assertThat(againStatus).isEqualTo("canceled");

        // ── Devolución rf-020 que la pasarela tampoco recibió ────────────────
        String p312 = seedCaptured("ch-312");
        gatewayDoesNotAnswer(GatewayCall.REFUND);
        Response refunding = refund(p312, "{\"refundRequestId\": \"rf-020\", \"amount\": 4.00}");
        assertThat(refunding.status()).as(refunding.body()).isEqualTo(200);
        gatewayReports("ch-312", "CAPTURED");
        ageAwaiting(p312);
        runSweep();
        awaitStatus(p312, "captured");
        awaitEvents("PaymentActionRejected", "ch-312", 1);
        Map<String, Object> failedRefund = exp("id", NOT_NULL, "refundRequestId", "rf-020", "amount", 4.0,
                "reason", null, "status", "failed", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p312);
        assertPayment(readPayment(p312), 200, payment("id", p312, "chargeRequestId", "ch-312", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "captured", "gatewayPaymentId", NOT_NULL,
                "refundedAmount", 0, "refunds", List.of(failedRefund)));
        assertEvent("PaymentActionRejected", "ch-312",
                rejectedData(p312, "ch-312", "refund", "rf-020", 4.0, "captured"));

        // ── rf-021 de 6.00 que la pasarela completó por 5.00 ─────────────────
        String p313 = seedCaptured("ch-313");
        gatewayDoesNotAnswer(GatewayCall.REFUND);
        Response lost = refund(p313, "{\"refundRequestId\": \"rf-021\", \"amount\": 6.00}");
        assertThat(lost.status()).as(lost.body()).isEqualTo(200);
        gatewayReportsRefundedTotal("ch-313", "500");
        ageAwaiting(p313);
        runSweep();
        awaitStatus(p313, "captured");
        awaitEvents("PaymentRefunded", "ch-313", 1);
        Map<String, Object> informed = exp("id", NOT_NULL, "refundRequestId", "rf-021", "amount", 5.0,
                "reason", null, "status", "succeeded", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p313);
        assertPayment(readPayment(p313), 200, payment("id", p313, "chargeRequestId", "ch-313", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "captured", "gatewayPaymentId", NOT_NULL,
                "refundedAmount", 5.0, "refunds", List.of(informed)));
        assertEvent("PaymentRefunded", "ch-313", refundedData(p313, "ch-313", "rf-021", 5.0, 5.0, "captured"));

        // ── ch-314: rf-022 de 4.00 hecha, rf-023 de 6.00 informada por 7.00 ──
        String p314 = seedCaptured("ch-314");
        gatewayRefunds("ch-314", new BigDecimal("4.00"));
        Response done = refund(p314, "{\"refundRequestId\": \"rf-022\", \"amount\": 4.00}");
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        awaitEvents("PaymentRefunded", "ch-314", "refundRequestId", "rf-022", 1);
        gatewayDoesNotAnswer(GatewayCall.REFUND);
        Response lostAgain = refund(p314, "{\"refundRequestId\": \"rf-023\", \"amount\": 6.00}");
        assertThat(lostAgain.status()).as(lostAgain.body()).isEqualTo(200);
        // La pasarela acumula 4.00 + 7.00: informa de 7.00 para rf-023, que supera lo que quedaba.
        gatewayReportsRefundedTotal("ch-314", "1100");
        ageAwaiting(p314);
        runSweep();
        awaitStatus(p314, "refunded");
        Map<String, Object> rf022 = exp("id", NOT_NULL, "refundRequestId", "rf-022", "amount", 4.0,
                "reason", null, "status", "succeeded", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p314);
        Map<String, Object> rf023 = exp("id", NOT_NULL, "refundRequestId", "rf-023", "amount", 6.0,
                "reason", null, "status", "succeeded", "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", p314);
        assertPayment(readPayment(p314), 200, payment("id", p314, "chargeRequestId", "ch-314", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "refunded", "gatewayPaymentId", NOT_NULL,
                "refundedAmount", 10.0, "refunds", List.of(rf022, rf023)));
        assertEvent("PaymentRefunded", "ch-314", "refundRequestId", "rf-023",
                refundedData(p314, "ch-314", "rf-023", 6.0, 10.0, "refunded"));
    }
}
