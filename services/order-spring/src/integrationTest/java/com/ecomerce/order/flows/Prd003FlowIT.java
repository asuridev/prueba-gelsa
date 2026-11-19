package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Prd003FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-PRD-003: el producto que la copia no tiene")
    void productMissingFromReplica() {
        Response response = createOrder(ana(), "k-ana-d03", orderBody(TOKEN_OK, line(P1, 1)));

        assertError(response, 422, "PRODUCT_NOT_AVAILABLE");
        assertThat(charges()).isZero();
    }
}
