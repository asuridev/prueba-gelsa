package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Prd001FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    private Response order(String key) {
        return createOrder(ana(), key, orderBody(TOKEN_OK, line(P1, 1)));
    }

    @Test
    @Order(1)
    @DisplayName("FL-PRD-001: el alta y el cambio de precio se aplican, la reentrega no tiene efecto")
    void upsertAppliesAndRedeliveryHasNoEffect() {
        deliverProductUpserted("e-p1-v1", productJson(P1, "SKU-001", "Camiseta", "12.50", "EUR", 1));
        awaitProductVersion(P1, 1);
        Response first = order("k-ana-d01a");
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String firstId = idOf(first);
        assertAmount(number(first, "$.lines[0].unitPrice"), "12.50");
        assertAmount(number(first, "$.totalAmount"), "12.50");

        deliverProductUpserted("e-p1-v2", productJson(P1, "SKU-001", "Camiseta", "15.00", "EUR", 2));
        awaitProductVersion(P1, 2);
        Response second = order("k-ana-d01b");
        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertAmount(number(second, "$.lines[0].unitPrice"), "15.00");
        Response frozen = getOrder(ana(), firstId);
        assertAmount(number(frozen, "$.lines[0].unitPrice"), "12.50");

        // Reentrega de la versión vieja: se descarta.
        deliverProductUpserted("e-p1-v1", productJson(P1, "SKU-001", "Camiseta", "12.50", "EUR", 1));
        holdsFor(Duration.ofSeconds(4), () -> productVersionOf(P1) == 2);
        Response third = order("k-ana-d01c");
        assertThat(third.status()).as(third.body()).isEqualTo(201);
        assertAmount(number(third, "$.lines[0].unitPrice"), "15.00");

        // Precio 0: se procesa sin ir a la deadLetter y el producto deja de poder pedirse.
        deliverProductUpserted("e-p1-bad", productJson(P1, "SKU-001", "Camiseta", "0", "EUR", 3));
        await(Duration.ofSeconds(30), () -> order("k-ana-d01-poll-" + System.nanoTime()).status() == 422);
        assertError(order("k-ana-d01d"), 422, "PRODUCT_NOT_AVAILABLE");
        assertThat(deadLetterMessages("ProductUpserted", 1)).isEmpty();
    }
}
