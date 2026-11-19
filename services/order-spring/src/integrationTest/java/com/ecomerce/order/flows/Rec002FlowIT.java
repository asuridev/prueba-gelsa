package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec002FlowIT extends OrderFlowSupport {

    private String o1;
    private String o2;

    @BeforeAll
    void prepare() {
        resetState();
        stubConnectionFault("POST", "/payments");
        stubCapture(PAY1, "captured");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            o1 = idOf(createdOrder(ana(), "k-ana-r02a", orderBody(TOKEN_OK, line(P1, 1))));
            o2 = idOf(createdOrder(ana(), "k-ana-r02b", orderBody(TOKEN_OK, line(P2, 1))));
            stubQuery(queryOf(o1), PAY1, "captured");
            purgeMessages(NOTIFICATIONS);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-002: rescate de un pedido pendiente que payment sí cobró, sin tocar lo que acaba de entrar en espera")
    void rescuesPaidOrderAndLeavesFreshOne() {
        // La retención de o2 va ANTES que el envejecimiento de o1, que ya despierta el barrido.
        holdFromReconciliation("chargeOrder", o2);
        ageForReconciliation("chargeOrder", o1);

        awaitStatusAna(o1, "paid");
        Response order = getOrder(ana(), o1);
        String paymentId = jsonPath(order, "$.paymentId");
        assertThat(paymentId).isEqualTo(PAY1);
        assertEmailRequested(o1, "paid", "order-paid", "ana", "12.50", "EUR", null);
        assertThat(statusAssertedAna(o2)).isEqualTo("pendingPayment");
        assertThat(stubCallCount("GET", queryOf(o2))).isZero();

        sweepCycle();
        pause(Duration.ofSeconds(6));
        sweepCycle();
        holdsFor(Duration.ofSeconds(10),
                () -> "pendingPayment".equals(statusOfAna(o2)) && stubCallCount("GET", queryOf(o2)) == 0);
    }
}
