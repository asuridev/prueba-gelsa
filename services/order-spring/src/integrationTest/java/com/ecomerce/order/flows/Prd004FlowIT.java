package com.ecomerce.order.flows;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Prd004FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-PRD-004: la retirada que llega antes que el alta")
    void retirementArrivesBeforeUpsert() {
        deliverProductRetired("e-p3-ret", "{\"productId\":\"" + P3 + "\",\"version\":2}");
        awaitProductVersion(P3, 2);
        deliverProductUpserted("e-p3-v1", productJson(P3, "SKU-003", "Libro", "20.00", "USD", 1));
        holdsFor(Duration.ofSeconds(4), () -> productVersionOf(P3) == 2);

        Response response = createOrder(ana(), "k-ana-d04", orderBody(TOKEN_OK, line(P3, 1)));

        assertError(response, 422, "PRODUCT_NOT_AVAILABLE");
    }
}
