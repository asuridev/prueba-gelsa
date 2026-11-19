package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord006FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubSequence("POST", "/payments",
                StubResponse.ok(201, chargeJson(PAY1, "captured", null, null)),
                StubResponse.ok(201, chargeJson("bbbbbbbb-0000-4000-8000-000000000002", "canceled", null, null)));
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-006: payment devuelve un cobro ya capturado o anulado")
    void paymentReturnsAlreadyCapturedOrCanceled() {
        purgeMessages(NOTIFICATIONS);

        Response first = createOrder(ana(), "k-ana-006a", orderBody(TOKEN_OK, line(P1, 1)));

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String statusFirst = jsonPath(first, "$.status");
        String paymentFirst = jsonPath(first, "$.paymentId");
        assertThat(statusFirst).isEqualTo("authorized");
        assertThat(paymentFirst).isEqualTo(PAY1);

        Response second = createOrder(ana(), "k-ana-006b", orderBody(TOKEN_OK, line(P2, 1)));

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        String o2 = idOf(second);
        String statusSecond = jsonPath(second, "$.status");
        assertThat(statusSecond).isEqualTo("canceled");
        assertEmailRequested(o2, "canceled", "order-canceled", "ana", "7.25", "EUR", null);
    }
}
