package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec001FlowIT extends OrderFlowSupport {

    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        // payment no contesta (corte de conexión: reintentable, y rápido).
        stubConnectionFault("POST", "/payments");
        stubFor("GET", QUERY_ANY, 404, errorJson("PAYMENT_NOT_FOUND"));
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-r01", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("pendingPayment");
            purgeMessages(NOTIFICATIONS);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-001: el cobro que payment nunca registró se da por fallido")
    void unregisteredChargeIsMarkedFailed() {
        ageForReconciliation("chargeOrder", o1);

        awaitStatusAna(o1, "paymentFailed");
        Response order = getOrder(ana(), o1);
        assertOrder(order, o1, "ana", "paymentFailed", "EUR", "12.50", null, null, "notReceived", p1(1));
        assertThat(stubCallCount("GET", queryOf(o1))).isEqualTo(1);
        assertEmailRequested(o1, "payment-failed", "order-payment-failed", "ana", "12.50", "EUR", "notReceived");

        sweepCycle();
        holdsFor(Duration.ofSeconds(10),
                () -> stubCallCount("GET", queryOf(o1)) == 1 && notificationCountOf(o1) == 1);
    }
}
