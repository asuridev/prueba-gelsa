package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can003FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/7\"}";
    private String o1;
    private String o2;

    @BeforeAll
    void prepare() {
        resetState();
        // Primer alta: cobro PAY1; segundo: PAY2.
        stubSequence("POST", "/payments",
                StubResponse.ok(201, chargeJson(PAY1, "actionRequired", null, ACTION)),
                StubResponse.ok(201, chargeJson(PAY2, "actionRequired", null, ACTION)));
        stubFor("POST", "/payments/" + PAY1 + "/cancel", 409, errorJson("PAYMENT_NOT_CANCELABLE"));
        stubFor("POST", "/payments/" + PAY2 + "/cancel", 422, errorJson("CANCEL_REJECTED"));
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            o1 = idOf(createdOrder(ana(), "k-ana-c03a", orderBody(TOKEN_OK, line(P1, 1))));
            o2 = idOf(createdOrder(ana(), "k-ana-c03b", orderBody(TOKEN_OK, line(P1, 1))));
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-003: payment ya no admite anular, o rechaza la anulación")
    void paymentAdvancedOrRejectsCancellation() {
        assertError(cancelOrder(ana(), o1), 409, "ORDER_PAYMENT_ADVANCED");
        Response first = getOrder(ana(), o1);
        assertOrder(first, o1, "ana", "actionRequired", "EUR", "12.50", PAY1, ACTION, null, p1(1));

        assertError(cancelOrder(ana(), o2), 422, "ORDER_CANCEL_REJECTED");
        Response second = getOrder(ana(), o2);
        assertOrder(second, o2, "ana", "actionRequired", "EUR", "12.50", PAY2, ACTION, null, p1(1));

        assertNoEmails(Duration.ofSeconds(3));
        assertThat(charges()).isEqualTo(2);
    }
}
