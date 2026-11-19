package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay005FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/5\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        stubCancel(PAY1, "canceling", null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-p05", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            Response canceled = cancelOrder(ana(), o1);
            assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
            String status = jsonPath(canceled, "$.status");
            assertThat(status).isEqualTo("canceling");
        });
    }

    private String rejection(String action, String status) {
        return paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"action\":\"" + action
                + "\",\"refundRequestId\":null,\"refundAmount\":null,\"status\":\"" + status + "\"")
                .replace(",\"amount\":12.50,\"currency\":\"EUR\"", "");
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-005: payment rechaza la anulación y el pedido vuelve")
    void paymentRejectsCancellation() {
        String event = rejection("cancel", "actionRequired");

        deliverPaymentActionRejected("e-rej-1", event);

        awaitStatusAna(o1, "actionRequired");
        Response order = getOrder(ana(), o1);
        assertOrder(order, o1, "ana", "actionRequired", "EUR", "12.50", PAY1, ACTION, null, p1(1));

        String snapshot = order.body();
        deliverPaymentActionRejected("e-rej-1", event);
        assertOrderUnchanged(o1, snapshot);

        deliverPaymentActionRejected("e-rej-2", rejection("capture", "authorized"));
        assertOrderUnchanged(o1, snapshot);
    }
}
