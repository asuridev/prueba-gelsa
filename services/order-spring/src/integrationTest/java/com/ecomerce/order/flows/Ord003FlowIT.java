package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord003FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("failed", PAY1, "insufficientFunds", null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-003: el cobro falla al momento y se encarga el correo de fallo")
    void chargeFailsImmediately() {
        purgeMessages(NOTIFICATIONS);

        Response response = createOrder(ana(), "k-ana-003", orderBody(paymentToken("tok_no_funds"), line(P1, 2)));

        assertThat(response.status()).as("un rechazo del cobro no es un error HTTP: " + response.body()).isEqualTo(201);
        String id = idOf(response);
        assertOrder(response, id, "ana", "paymentFailed", "EUR", "25.00", PAY1, null, "insufficientFunds", p1(2));
        assertEmailRequested(id, "payment-failed", "order-payment-failed", "ana", "25.00", "EUR", "insufficientFunds");
    }
}
