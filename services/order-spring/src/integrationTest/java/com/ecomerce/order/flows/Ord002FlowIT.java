package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord002FlowIT extends OrderFlowSupport {

    private static final String METHOD = "cccccccc-0000-4000-8000-000000000001";

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, REDIRECT_3DS_1);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-002: el cobro necesita 3DS")
    void chargeNeedsThreeDs() {
        purgeMessages(NOTIFICATIONS);

        Response response = createOrder(ana(), "k-ana-002", orderBody(paymentMethod(METHOD), line(P1, 1)));

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        String id = idOf(response);
        assertThat(response.header("Location")).endsWith("/api/v1/orders/" + id);
        assertOrder(response, id, "ana", "actionRequired", "EUR", "12.50", PAY1, REDIRECT_3DS_1, null, p1(1));
        Object action = jsonPath(response, "$.customerAction");
        assertThat(action).isInstanceOf(java.util.Map.class);

        List<String> charges = stubRequests("POST", "/payments");
        assertThat(charges).hasSize(1);
        assertChargeBody(charges.get(0), id, "ana", "12.50", "EUR", null, METHOD);

        assertNoEmails(Duration.ofSeconds(3));
    }
}
