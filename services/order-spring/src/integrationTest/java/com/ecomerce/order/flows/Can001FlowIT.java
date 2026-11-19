package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can001FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/6\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        stubCancel(PAY1, "canceled", null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-c01", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("actionRequired");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-001: anular un pedido que espera el 3DS")
    void cancelsOrderAwaitingThreeDs() {
        purgeMessages(NOTIFICATIONS);

        Response canceled = cancelOrder(ana(), o1);

        assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
        assertOrder(canceled, o1, "ana", "canceled", "EUR", "12.50", PAY1, null, null, p1(1));
        assertThat(cancelsOf(PAY1)).isEqualTo(1);
        assertEmailRequested(o1, "canceled", "order-canceled", "ana", "12.50", "EUR", null);

        Response again = cancelOrder(ana(), o1);
        assertThat(again.status()).as(again.body()).isEqualTo(200);
        assertOrder(again, o1, "ana", "canceled", "EUR", "12.50", PAY1, null, null, p1(1));
        holdsFor(Duration.ofSeconds(3), () -> cancelsOf(PAY1) == 1 && notificationCountOf(o1) == 1);

        deliverPaymentCanceled("e-can-c1", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"requested\":true"));
        holdsFor(Duration.ofSeconds(6),
                () -> "canceled".equals(statusOfAna(o1)) && notificationCountOf(o1) == 1);
    }
}
