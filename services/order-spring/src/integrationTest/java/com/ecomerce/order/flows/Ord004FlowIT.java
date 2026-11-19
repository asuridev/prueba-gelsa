package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord004FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        // Primera llamada: 422 PAYMENT_METHOD_UNAVAILABLE; segunda: 400 CURRENCY_UNKNOWN.
        stubSequence("POST", "/payments",
                StubResponse.ok(422, errorJson("PAYMENT_METHOD_UNAVAILABLE")),
                StubResponse.ok(400, errorJson("CURRENCY_UNKNOWN")));
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-004: payment rechaza el medio, o rechaza la petición por otro motivo")
    void paymentRejectsRequest() {
        purgeMessages(NOTIFICATIONS);
        Response first = createOrder(ana(), "k-ana-004a",
                orderBody(paymentMethod("cccccccc-0000-4000-8000-000000000009"), line(P1, 1)));

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String o1 = idOf(first);
        assertOrder(first, o1, "ana", "paymentFailed", "EUR", "12.50", null, null, "invalidPaymentMethod", p1(1));
        assertEmailRequested(o1, "payment-failed", "order-payment-failed", "ana", "12.50", "EUR", "invalidPaymentMethod");

        Response second = createOrder(ana(), "k-ana-004b", orderBody(paymentToken("tok_x"), line(P2, 1)));

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        String o2 = idOf(second);
        assertOrder(second, o2, "ana", "paymentFailed", "EUR", "7.25", null, null, "processingError", p2(1));
        assertEmailRequested(o2, "payment-failed", "order-payment-failed", "ana", "7.25", "EUR", "processingError");
    }
}
