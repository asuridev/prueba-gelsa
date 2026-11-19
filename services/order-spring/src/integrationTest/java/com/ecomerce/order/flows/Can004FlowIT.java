package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can004FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/8\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        stubCancel(PAY1, "authorized", null);
        stubCapture(PAY1, "captured");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-c04", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("actionRequired");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-004: la anulación llega tarde y payment responde autorizado")
    void cancellationArrivesLate() {
        Response canceled = cancelOrder(ana(), o1);

        assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
        assertOrder(canceled, o1, "ana", "authorized", "EUR", "12.50", PAY1, null, null, p1(1));

        deliverPaymentAuthorized("e-auth-c4", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"gatewayPaymentId\":\"gw_c4\""));

        await(Duration.ofSeconds(20), () -> capturesOf(PAY1) == 1);
        holdsFor(Duration.ofSeconds(5), () -> capturesOf(PAY1) == 1 && "authorized".equals(statusOfAna(o1)));
    }
}
