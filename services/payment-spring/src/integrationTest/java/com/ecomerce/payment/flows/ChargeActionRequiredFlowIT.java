package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeActionRequiredFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-003: la pasarela pide autenticar al cliente")
    void gatewayAsksToAuthenticateTheCustomer() {
        gatewayRequiresAction("ch-010");
        Response created = charge("ch-010", "cus-1", "60.00", "EUR", tokenSource());

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String p10 = idOf(created);
        Map<String, Object> first = JsonPath.read(created.body(), "$");
        assertThat(first.get("customerAction")).as("customerAction viaja como objeto JSON").isInstanceOf(Map.class);
        assertThat(first.get("awaitingSince")).isNotNull();
        assertThat(first.get("actionRequiredSince")).isNotNull();
        assertThat(first.get("gatewayPaymentId")).isNotNull();
        assertPayment(created, 201, payment("chargeRequestId", "ch-010", "customerRef", "cus-1", "amount", 60.0,
                "currency", "EUR", "status", "actionRequired", "gatewayPaymentId", NOT_NULL,
                "awaitingSince", NOT_NULL, "actionRequiredSince", NOT_NULL, "customerAction", NOT_NULL));

        // getPayment devuelve lo mismo, con la misma customerAction.
        assertPayment(readPayment(p10), 200, payment("id", p10, "chargeRequestId", "ch-010", "customerRef", "cus-1",
                "amount", 60.0, "currency", "EUR", "status", "actionRequired",
                "gatewayPaymentId", first.get("gatewayPaymentId"), "awaitingSince", first.get("awaitingSince"),
                "actionRequiredSince", first.get("actionRequiredSince"),
                "customerAction", first.get("customerAction")));

        assertEvent("PaymentActionRequired", "ch-010", eventData(p10, "ch-010", "cus-1", 60.0,
                "customerAction", first.get("customerAction")));

        // When: la pasarela avisa de que el cliente se autenticó.
        gatewayReports("ch-010", "AUTHORIZED");
        Response notice = sendGatewayNotice("ch-010");
        assertThat(notice.status()).as(notice.body()).isLessThan(300);

        await(Duration.ofSeconds(10), () -> "authorized".equals(statusOf(p10)));
        assertPayment(readPayment(p10), 200, payment("id", p10, "chargeRequestId", "ch-010", "customerRef", "cus-1",
                "amount", 60.0, "currency", "EUR", "status", "authorized",
                "gatewayPaymentId", first.get("gatewayPaymentId")));
        assertEvent("PaymentAuthorized", "ch-010", eventData(p10, "ch-010", "cus-1", 60.0,
                "gatewayPaymentId", first.get("gatewayPaymentId")));

        // When: el mismo aviso otra vez: no hace nada.
        Response again = sendGatewayNotice("ch-010");
        assertThat(again.status()).as(again.body()).isLessThan(300);
        holdsFor(Duration.ofSeconds(3), () -> "authorized".equals(statusOf(p10))
                && eventsOf("PaymentAuthorized", "ch-010").size() == 1);
    }
}
