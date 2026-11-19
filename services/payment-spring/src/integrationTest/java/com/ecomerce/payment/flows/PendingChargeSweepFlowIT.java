package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PendingChargeSweepFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-001: un cobro sin respuesta se resuelve preguntando")
    void unansweredChargeIsResolvedByAsking() {
        // Given/When: la pasarela no contesta al cobro, pero lo registra.
        gatewayDoesNotAnswer(GatewayCall.CHARGE);
        Response created = charge("ch-300", "cus-1", "12.00", "EUR", tokenSource());
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String p300 = idOf(created);
        assertPayment(created, 201, payment("id", p300, "chargeRequestId", "ch-300", "customerRef", "cus-1",
                "amount", 12.0, "currency", "EUR", "status", "pending", "awaitingSince", NOT_NULL));
        assertThat(chargeCalls("ch-300")).isEqualTo(1);

        // When: la pasarela vuelve a contestar (ch-300 quedó autorizado), se envejece la marca y pasa un ciclo.
        gatewayReports("ch-300", "AUTHORIZED");
        ageAwaiting(p300);
        runSweep();
        awaitStatus(p300, "authorized");

        Response resolved = readPayment(p300);
        String gatewayPaymentId = jsonPath(resolved, "$.gatewayPaymentId");
        assertThat(gatewayPaymentId).isNotBlank();
        assertPayment(resolved, 200, payment("id", p300, "chargeRequestId", "ch-300", "customerRef", "cus-1",
                "amount", 12.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId));
        assertEvent("PaymentAuthorized", "ch-300", eventData(p300, "ch-300", "cus-1", 12.0,
                "gatewayPaymentId", gatewayPaymentId));
        assertThat(chargeCalls("ch-300")).as("el barrido consulta, no vuelve a cobrar").isEqualTo(1);

        // When: lo mismo con ch-301 (la pasarela no lo registró) y ch-302 (registrado y cancelado por ella).
        gatewayDoesNotAnswer(GatewayCall.CHARGE);
        Response c301 = charge("ch-301", "cus-1", "12.00", "EUR", tokenSource());
        Response c302 = charge("ch-302", "cus-1", "12.00", "EUR", tokenSource());
        assertThat(c301.status()).as(c301.body()).isEqualTo(201);
        assertThat(c302.status()).as(c302.body()).isEqualTo(201);
        String p301 = idOf(c301);
        String p302 = idOf(c302);
        gatewayReports("ch-301", "NOT_FOUND");
        gatewayReports("ch-302", "CANCELED");
        ageAwaiting(p301);
        ageAwaiting(p302);
        runSweep();
        awaitStatus(p301, "failed");
        awaitStatus(p302, "canceled");

        Response failed = readPayment(p301);
        assertPayment(failed, 200, payment("id", p301, "chargeRequestId", "ch-301", "customerRef", "cus-1",
                "amount", 12.0, "currency", "EUR", "status", "failed", "failureReason", "notReceived"));
        Map<String, Object> failedData = eventData(p301, "ch-301", "cus-1", 12.0, "failureReason", "notReceived");
        assertEvent("PaymentFailed", "ch-301", failedData);

        Response canceled = readPayment(p302);
        String canceledStatus = jsonPath(canceled, "$.status");
        assertThat(canceledStatus).isEqualTo("canceled");
        assertEvent("PaymentCanceled", "ch-302", eventData(p302, "ch-302", "cus-1", 12.0, "requested", false));
    }
}
