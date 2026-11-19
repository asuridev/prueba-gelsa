package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class Ord010FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-010: la cabecera de idempotencia es obligatoria")
    void idempotencyHeaderIsRequired() {
        Response missing = exchangeWithoutIdempotencyKey(HttpMethod.POST, ORDERS, orderBody(TOKEN_OK, line(P1, 1)), ana());

        assertError(missing, 400, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(charges()).isZero();
        Response list = get(ORDERS, ana());
        Integer total = jsonPath(list, "$.totalElements");
        assertThat(total).isZero();

        // Precedencia: la forma del cuerpo se valida antes que la cabecera.
        Response invalidShape = exchangeWithoutIdempotencyKey(HttpMethod.POST, ORDERS, orderBody(TOKEN_OK), ana());
        assertError(invalidShape, 400, "VALIDATION_ERROR");
    }
}
