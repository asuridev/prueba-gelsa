package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec007FlowIT extends OrderFlowSupport {

    /** o[0] = {o1} … o[4] = {o5}. */
    private final List<String> stalled = new ArrayList<>();
    private String o6;

    @BeforeAll
    void prepare() {
        resetState();
        stubConnectionFault("POST", "/payments");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            for (int i = 1; i <= 5; i++) {
                Response created = createdOrder(ana(), "k-ana-r07-" + i, orderBody(TOKEN_OK, line(P1, 1)));
                String status = jsonPath(created, "$.status");
                assertThat(status).isEqualTo("pendingPayment");
                stalled.add(idOf(created));
            }
            o6 = idOf(createdOrder(ana(), "k-ana-r07-6", orderBody(TOKEN_OK, line(P1, 1))));
            stubQuery(QUERY_ANY, PAY1, "authorized");
            stubCapture(PAY1, "captured");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-007: el barrido en clúster, dos réplicas y una sola llamada por pedido")
    void clusterSweepQueriesOncePerOrder() {
        startReplica();
        try {
            holdFromReconciliation("chargeOrder", o6);
            for (String id : stalled) {
                ageForReconciliation("chargeOrder", id);
            }

            await(Duration.ofSeconds(90), () -> {
                for (String id : stalled) {
                    String status = statusOfAna(id);
                    if (!"authorized".equals(status) && !"paid".equals(status)) {
                        return false;
                    }
                }
                return true;
            });

            holdsFor(Duration.ofSeconds(8), () -> {
                for (String id : stalled) {
                    if (stubCallCount("GET", queryOf(id)) != 1) {
                        return false;
                    }
                }
                return stubCallCount("GET", queryOf(o6)) == 0;
            });
            assertThat(statusAssertedAna(o6)).isEqualTo("pendingPayment");
            assertThat(stubCallCount("GET", QUERY_ANY)).isEqualTo(5);
        } finally {
            stopReplica();
        }
    }
}
