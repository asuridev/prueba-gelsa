package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec003FlowIT extends OrderFlowSupport {

    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubConnectionFault("POST", "/payments");
        stubQuery(QUERY_ANY, PAY1, "pending");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-r03", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("pendingPayment");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-003: un cobro aún pendiente en payment renueva la espera")
    void pendingChargeRenewsTheWait() {
        ageForReconciliation("chargeOrder", o1);

        await(Duration.ofSeconds(45), () -> stubCallCount("GET", queryOf(o1)) >= 1);
        assertThat(stubCallCount("GET", queryOf(o1))).isEqualTo(1);
        assertThat(statusAssertedAna(o1)).isEqualTo("pendingPayment");

        int before = stubCallCount("GET", queryOf(o1));
        sweepCycle();
        holdsFor(Duration.ofSeconds(10),
                () -> stubCallCount("GET", queryOf(o1)) - before <= 1 && "pendingPayment".equals(statusOfAna(o1)));
    }
}
