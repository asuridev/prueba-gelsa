package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay007FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/5\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        stubCancel(PAY1, "canceling", null);
        stubCapture(PAY1, "captured");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-p07", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            Response canceled = cancelOrder(ana(), o1);
            assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
            String status = jsonPath(canceled, "$.status");
            assertThat(status).isEqualTo("canceling");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-007: autorizado desde canceling, el 3DS se completó mientras se anulaba")
    void authorizedFromCanceling() {
        deliverPaymentAuthorized("e-auth-7", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"gatewayPaymentId\":\"gw_7\""));

        awaitStatusAna(o1, "authorized");
        Response order = getOrder(ana(), o1);
        Object action = jsonPath(order, "$.customerAction");
        assertThat(action).isNull();
        await(Duration.ofSeconds(20), () -> capturesOf(PAY1) == 1);
        holdsFor(Duration.ofSeconds(4), () -> capturesOf(PAY1) == 1);
    }
}
