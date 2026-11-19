package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class Ord011FlowIT extends OrderFlowSupport {

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
    @DisplayName("FL-ORD-011: el token del comprador no trae un email utilizable")
    void tokenWithoutUsableEmail() {
        String body = orderBody(TOKEN_OK, line(P1, 1));

        assertError(createOrder(evaWithoutEmail(), "k-eva-011a", body), 422, "CUSTOMER_EMAIL_REQUIRED");
        assertError(createOrder(evaWithBadEmail(), "k-eva-011b", body), 422, "CUSTOMER_EMAIL_REQUIRED");

        // Precedencia: el email del token va antes que la cabecera de idempotencia.
        Response noKey = exchangeWithoutIdempotencyKey(HttpMethod.POST, ORDERS, body, evaWithoutEmail());
        assertError(noKey, 422, "CUSTOMER_EMAIL_REQUIRED");

        // Precedencia: la forma del cuerpo va antes que el email del token.
        assertError(createOrder(evaWithoutEmail(), "k-eva-011c", orderBody(TOKEN_OK)), 400, "VALIDATION_ERROR");

        assertThat(charges()).as("payment no recibió ninguna llamada").isZero();
    }
}
