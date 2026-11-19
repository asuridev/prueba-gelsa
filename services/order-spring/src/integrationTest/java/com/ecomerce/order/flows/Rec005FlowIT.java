package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec005FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/11\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        stubConnectionFault("POST", "/payments/" + PAY1 + "/cancel");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            o1 = idOf(createdOrder(ana(), "k-ana-r05", orderBody(TOKEN_OK, line(P1, 1))));
            Response canceled = cancelOrder(ana(), o1);
            assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
            String status = jsonPath(canceled, "$.status");
            assertThat(status).isEqualTo("canceling");
            stubCancel(PAY1, "canceled", null);
            purgeMessages(NOTIFICATIONS);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-005: la anulación que no se confirmó se vuelve a pedir")
    void unconfirmedCancellationIsRequestedAgain() {
        int before = cancelsOf(PAY1);

        ageForReconciliation("cancelOrderPayment", o1);

        await(Duration.ofSeconds(45), () -> cancelsOf(PAY1) == before + 1);
        awaitStatusAna(o1, "canceled");
        assertEmailRequested(o1, "canceled", "order-canceled", "ana", "12.50", "EUR", null);
    }
}
