package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Rec004FlowIT extends OrderFlowSupport {

    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            // El PaymentAuthorized no llega: nadie ha pedido la captura.
            Response created = createdOrder(ana(), "k-ana-r04", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("authorized");
            stubCapture(PAY1, "captured");
            purgeMessages(NOTIFICATIONS);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-004: la captura que no se confirmó se vuelve a pedir")
    void unconfirmedCaptureIsRequestedAgain() {
        int before = capturesOf(PAY1);

        ageForReconciliation("captureOrderPayment", o1);

        await(Duration.ofSeconds(45), () -> capturesOf(PAY1) == before + 1);
        awaitStatusAna(o1, "paid");
        assertEmailRequested(o1, "paid", "order-paid", "ana", "12.50", "EUR", null);

        sweepCycle();
        holdsFor(Duration.ofSeconds(10), () -> capturesOf(PAY1) == before + 1);
    }
}
