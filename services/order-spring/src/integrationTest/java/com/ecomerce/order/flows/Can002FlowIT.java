package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can002FlowIT extends OrderFlowSupport {

    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
        stubCapture(PAY1, "captured");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-c02", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
            deliverPaymentAuthorized("e-auth-c2", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"gatewayPaymentId\":\"gw_c2\""));
            deliverPaymentCaptured("e-cap-c2", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", null));
            awaitStatusAna(o1, "paid");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-002: no se puede anular un pedido que no espera el 3DS")
    void cannotCancelOrderNotAwaitingThreeDs() {
        assertError(cancelOrder(ana(), o1), 409, "ORDER_NOT_CANCELABLE");
        assertThat(statusAssertedAna(o1)).isEqualTo("paid");
        assertThat(cancelsOf(PAY1)).isZero();

        // La propiedad se comprueba antes que el estado.
        assertError(cancelOrder(luis(), o1), 403, "ORDER_NOT_OWNED");

        assertError(cancelOrder(ana(), MISSING_ORDER), 404, "ORDER_NOT_FOUND");
    }
}
