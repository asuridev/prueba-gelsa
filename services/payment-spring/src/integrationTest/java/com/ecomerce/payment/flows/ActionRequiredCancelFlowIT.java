package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ActionRequiredCancelFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-004: se anula un cobro que espera la autenticación del cliente")
    void cancelsAPaymentWaitingForTheCustomer() {
        // Given: dos cobros en actionRequired, con su customerAction leída por getPayment.
        String p80 = seedActionRequired("ch-080");
        String p81 = seedActionRequired("ch-081");
        Map<String, Object> before = JsonPath.read(readPayment(p81).body(), "$");
        assertThat(before.get("customerAction")).isNotNull();

        // When: se anula ch-080.
        gatewayCancels("ch-080");
        Response canceled = cancel(p80);
        assertPayment(canceled, 200, payment("id", p80, "chargeRequestId", "ch-080", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "canceled", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentCanceled", "ch-080", eventData(p80, "ch-080", "cus-1", 10.0, "requested", true));

        // When: se anula ch-081 y la pasarela rechaza la anulación.
        gatewayRejects(GatewayCall.CANCEL);
        gatewayReports("ch-081", "ACTION_REQUIRED");
        assertError(cancel(p81), 422, "CANCEL_REJECTED");

        Response after = readPayment(p81);
        Map<String, Object> now = JsonPath.read(after.body(), "$");
        assertPayment(after, 200, payment("id", p81, "chargeRequestId", "ch-081", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "actionRequired",
                "gatewayPaymentId", before.get("gatewayPaymentId"), "awaitingSince", NOT_NULL,
                "actionRequiredSince", before.get("actionRequiredSince"),
                "customerAction", before.get("customerAction")));
        Instant awaitingBefore = Instant.parse((String) before.get("awaitingSince"));
        Instant awaitingNow = Instant.parse((String) now.get("awaitingSince"));
        assertThat(awaitingNow).as("awaitingSince posterior al que tenía").isAfter(awaitingBefore);

        assertEvent("PaymentActionRejected", "ch-081",
                rejectedData(p81, "ch-081", "cancel", null, null, "actionRequired"));
    }
}
