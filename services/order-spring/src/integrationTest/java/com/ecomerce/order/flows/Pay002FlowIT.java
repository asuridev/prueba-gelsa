package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay002FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/2\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("pending", PAY1, null, null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-p02", orderBody(TOKEN_OK, line(P2, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("pendingPayment");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-002: el cobro pasa a necesitar 3DS por evento")
    void chargeNeedsThreeDsByEvent() {
        String event = paymentEvent(PAY1, o1, "ana", "7.25", "EUR", "\"customerAction\":" + ACTION);

        deliverPaymentActionRequired("e-ar-1", event);

        awaitStatusAna(o1, "actionRequired");
        Response order = getOrder(ana(), o1);
        assertOrder(order, o1, "ana", "actionRequired", "EUR", "7.25", PAY1, ACTION, null, p2(1));

        String snapshot = order.body();
        deliverPaymentActionRequired("e-ar-1", event);
        assertOrderUnchanged(o1, snapshot);
    }
}
