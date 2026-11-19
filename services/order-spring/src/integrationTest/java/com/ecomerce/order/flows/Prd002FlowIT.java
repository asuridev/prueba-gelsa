package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Prd002FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    private Response orderP2(String key) {
        return createOrder(ana(), key, orderBody(TOKEN_OK, line(P2, 1)));
    }

    @Test
    @Order(1)
    @DisplayName("FL-PRD-002: el producto retirado no se puede pedir y una alta vieja no lo resucita")
    void retiredProductCannotBeOrdered() {
        deliverProductUpserted("e-p2-v1", productJson(P2, "SKU-002", "Gorra", "7.25", "EUR", 1));
        awaitProductVersion(P2, 1);

        deliverProductRetired("e-p2-ret", "{\"productId\":\"" + P2 + "\",\"version\":2}");
        awaitProductVersion(P2, 2);
        assertError(orderP2("k-ana-d02a"), 422, "PRODUCT_NOT_AVAILABLE");

        // Un alta antigua que llega tarde no resucita la lápida.
        deliverProductUpserted("e-p2-v1-late", productJson(P2, "SKU-002", "Gorra", "7.25", "EUR", 1));
        holdsFor(Duration.ofSeconds(4), () -> productVersionOf(P2) == 2);
        assertError(orderP2("k-ana-d02b"), 422, "PRODUCT_NOT_AVAILABLE");

        // Reentrega de la retirada: sin efecto.
        deliverProductRetired("e-p2-ret", "{\"productId\":\"" + P2 + "\",\"version\":2}");
        holdsFor(Duration.ofSeconds(4), () -> productVersionOf(P2) == 2);
        assertError(orderP2("k-ana-d02b2"), 422, "PRODUCT_NOT_AVAILABLE");
        assertThat(deadLetterMessages("ProductRetired", 1)).isEmpty();

        // catalog vuelve a ponerlo a la venta.
        deliverProductUpserted("e-p2-v3", productJson(P2, "SKU-002", "Gorra", "8.00", "EUR", 3));
        awaitProductVersion(P2, 3);
        Response back = orderP2("k-ana-d02c");
        assertThat(back.status()).as(back.body()).isEqualTo(201);
        assertAmount(number(back, "$.lines[0].unitPrice"), "8.00");
    }
}
