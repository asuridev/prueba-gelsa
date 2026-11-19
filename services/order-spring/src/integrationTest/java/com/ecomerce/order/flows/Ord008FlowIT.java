package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord008FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubForDelayed("POST", "/payments", 201, chargeJson(PAY1, "authorized", null, null), 500);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-008: dos peticiones con la misma clave a la vez")
    void sameKeyAtTheSameTime() {
        String body = orderBody(TOKEN_OK, line(P1, 1));
        // Calienta el token (alta de la persona) para que la carrera sea del servidor.
        assertThat(ana()).isNotBlank();

        List<Response> results = raceOf(2, () -> createOrder(ana(), "k-ana-008", body));

        Response a = results.get(0);
        Response b = results.get(1);
        boolean bothCreated = a.status() == 201 && b.status() == 201;
        if (bothCreated) {
            assertThat(idOf(a)).isEqualTo(idOf(b));
        } else {
            Response created = a.status() == 201 ? a : b;
            Response rejected = a.status() == 201 ? b : a;
            assertThat(created.status()).as(a.body() + " / " + b.body()).isEqualTo(201);
            assertError(rejected, 409, "IDEMPOTENCY_KEY_IN_PROGRESS");
        }

        Response list = get(ORDERS, ana());
        Integer total = jsonPath(list, "$.totalElements");
        assertThat(total).isEqualTo(1);
        assertThat(charges()).as("exactamente una llamada a payment").isEqualTo(1);
    }
}
