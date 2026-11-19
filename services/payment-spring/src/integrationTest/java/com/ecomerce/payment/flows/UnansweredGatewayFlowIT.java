package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class UnansweredGatewayFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-005: la pasarela que no contesta nunca")
    void gatewayThatNeverAnswers() {
        // Given: dos cobros en pending; la pasarela no contesta a las consultas de ch-340 y sí a las de ch-341.
        String p340 = seedPending("ch-340");
        String p341 = seedPending("ch-341");
        gatewayDoesNotAnswer(GatewayCall.STATUS);
        gatewayDoesNotAnswer(GatewayCall.SEARCH);
        gatewayReports("ch-341", "AUTHORIZED");
        ageAwaiting(p340);
        ageAwaiting(p341);

        // When: primer ciclo.
        runSweep();
        awaitStatus(p341, "authorized");
        await(Duration.ofSeconds(60), () -> statusQueries("ch-340") >= 1);
        assertThat(statusQueries("ch-340")).as("una consulta en el primer ciclo").isEqualTo(1);

        // When: segundo ciclo. El barrido renovó el awaitingSince de ch-340: todavía no supera el umbral.
        runSweep();
        holdsFor(Duration.ofSeconds(15), () -> statusQueries("ch-340") == 1);

        // Then.
        assertPayment(readPayment(p340), 200, payment("id", p340, "chargeRequestId", "ch-340", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "pending", "awaitingSince", NOT_NULL));
        for (String type : new String[] {"PaymentAuthorized", "PaymentFailed", "PaymentCanceled",
                "PaymentCaptured", "PaymentActionRequired"}) {
            assertThat(eventsOf(type, "ch-340")).as("%s de ch-340", type).isEmpty();
        }
        String gatewayPaymentId = jsonPath(readPayment(p341), "$.gatewayPaymentId");
        assertThat(gatewayPaymentId).isNotBlank();
        assertPayment(readPayment(p341), 200, payment("id", p341, "chargeRequestId", "ch-341", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId));
        assertEvent("PaymentAuthorized", "ch-341", eventData(p341, "ch-341", "cus-1", 10.0,
                "gatewayPaymentId", gatewayPaymentId));
    }
}
