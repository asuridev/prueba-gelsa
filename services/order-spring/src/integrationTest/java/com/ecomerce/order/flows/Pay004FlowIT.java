package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay004FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/4\"}";
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
            Response created = createdOrder(ana(), "k-ana-p04", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("pendingPayment");
            deliverPaymentActionRequired("e-ar-4",
                    paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"customerAction\":" + ACTION));
            awaitStatusAna(o1, "actionRequired");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-004: la autorización queda anulada por evento")
    void authorizationCanceledByEvent() {
        purgeMessages(NOTIFICATIONS);
        String canceled = paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"requested\":true");

        deliverPaymentCanceled("e-can-1", canceled);

        awaitStatusAna(o1, "canceled");
        Response order = getOrder(ana(), o1);
        assertOrder(order, o1, "ana", "canceled", "EUR", "12.50", PAY1, null, null, p1(1));
        assertEmailRequested(o1, "canceled", "order-canceled", "ana", "12.50", "EUR", null);

        String snapshot = order.body();
        deliverPaymentCanceled("e-can-1", canceled);
        assertOrderUnchanged(o1, snapshot);
        holdsFor(Duration.ofSeconds(3), () -> notificationCountOf(o1) == 1);

        // Un estado terminal no se abandona.
        deliverPaymentFailed("e-fail-9",
                paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"failureReason\":\"declined\""));
        assertOrderUnchanged(o1, snapshot);
        holdsFor(Duration.ofSeconds(3), () -> notificationsWithKey("order:" + o1 + ":payment-failed").isEmpty());
    }
}
