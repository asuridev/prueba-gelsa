package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord012FlowIT extends OrderFlowSupport {

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
            Response created = createdOrder(ana(), "k-ana-012", orderBody(TOKEN_OK, line(P1, 1)));
            o1 = idOf(created);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-012: un comprador no ve los pedidos de otro")
    void buyerDoesNotSeeOthersOrders() {
        assertError(getOrder(luis(), o1), 404, "ORDER_NOT_FOUND");

        Response list = get(ORDERS, luis());
        assertThat(list.status()).isEqualTo(200);
        assertBody(list, "{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}");

        assertError(getOrder(ana(), MISSING_ORDER), 404, "ORDER_NOT_FOUND");
    }
}
