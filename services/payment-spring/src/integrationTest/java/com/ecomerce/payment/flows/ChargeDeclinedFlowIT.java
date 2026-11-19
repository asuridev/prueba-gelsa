package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeDeclinedFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-002: la pasarela rechaza el cobro")
    void gatewayDeclinesTheCharge() {
        gatewayDeclines("ch-002", "insufficientFunds");
        Response declined = charge("ch-002", "cus-1", "40.00", "EUR", tokenSource());

        assertThat(declined.status()).as(declined.body()).isEqualTo(201);
        String p2 = idOf(declined);
        String gatewayPaymentId = jsonPath(declined, "$.gatewayPaymentId");
        assertThat(gatewayPaymentId).isNotBlank();
        assertPayment(declined, 201, payment("chargeRequestId", "ch-002", "customerRef", "cus-1", "amount", 40.0,
                "currency", "EUR", "status", "failed", "failureReason", "insufficientFunds",
                "gatewayPaymentId", gatewayPaymentId));

        Map<String, Object> data = eventData(p2, "ch-002", "cus-1", 40.0, "failureReason", "insufficientFunds");
        assertEvent("PaymentFailed", "ch-002", data);

        // failed es terminal: no se captura.
        assertError(capture(p2), 409, "PAYMENT_NOT_CAPTURABLE");

        // Una moneda sin decimales que la pasarela no admite: processingError.
        gatewayDeclines("ch-003", "processingError");
        Response unsupported = charge("ch-003", "cus-1", "5000", "XAF", tokenSource());
        assertThat(unsupported.status()).as(unsupported.body()).isEqualTo(201);
        String status = jsonPath(unsupported, "$.status");
        String reason = jsonPath(unsupported, "$.failureReason");
        assertThat(status).isEqualTo("failed");
        assertThat(reason).isEqualTo("processingError");
    }
}
