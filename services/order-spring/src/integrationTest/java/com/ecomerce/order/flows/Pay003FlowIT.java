package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay003FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/3\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-p03", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("actionRequired");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-003: el cobro falla por evento")
    void chargeFailsByEvent() {
        purgeMessages(NOTIFICATIONS);
        String event = paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"failureReason\":\"authenticationFailed\"");

        deliverPaymentFailed("e-fail-1", event);

        awaitStatusAna(o1, "paymentFailed");
        Response order = getOrder(ana(), o1);
        assertOrder(order, o1, "ana", "paymentFailed", "EUR", "12.50", PAY1, null, "authenticationFailed", p1(1));
        assertEmailRequested(o1, "payment-failed", "order-payment-failed", "ana", "12.50", "EUR",
                "authenticationFailed");

        String snapshot = order.body();
        deliverPaymentFailed("e-fail-1", event);
        assertOrderUnchanged(o1, snapshot);
        holdsFor(Duration.ofSeconds(3), () -> notificationCountOf(o1) == 1);
    }
}
