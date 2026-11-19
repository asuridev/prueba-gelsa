package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord007FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-007: reintento con la misma clave y clave reutilizada con otro contenido")
    void idempotentRetryAndKeyReuse() {
        String body = orderBody(TOKEN_OK, line(P1, 2));

        Response first = createOrder(ana(), "k-ana-007", body);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String o1 = idOf(first);
        String status1 = jsonPath(first, "$.status");
        assertThat(status1).isEqualTo("authorized");

        Response replay = createOrder(ana(), "k-ana-007", body);
        assertThat(replay.status()).as(replay.body()).isEqualTo(201);
        assertThat(idOf(replay)).isEqualTo(o1);
        assertBody(replay, first.body());
        assertThat(replay.header("Location")).endsWith("/api/v1/orders/" + o1);
        assertThat(charges()).as("una sola llamada a payment").isEqualTo(1);
        Response list = get(ORDERS, ana());
        assertThat(list.status()).isEqualTo(200);
        Integer total = jsonPath(list, "$.totalElements");
        assertThat(total).isEqualTo(1);

        Response reused = createOrder(ana(), "k-ana-007", orderBody(TOKEN_OK, line(P2, 1)));
        assertError(reused, 409, "IDEMPOTENCY_KEY_REUSED");

        Response other = createOrder(luis(), "k-ana-007", body);
        assertThat(other.status()).as(other.body()).isEqualTo(201);
        assertThat(idOf(other)).isNotEqualTo(o1);
        String customer = jsonPath(other, "$.customerRef");
        assertThat(customer).isEqualTo("luis");

        Response fresh = createOrder(ana(), "k-ana-007b", body);
        assertThat(fresh.status()).as(fresh.body()).isEqualTo(201);
        assertThat(idOf(fresh)).isNotEqualTo(o1);
        String freshCustomer = jsonPath(fresh, "$.customerRef");
        assertThat(freshCustomer).isEqualTo("ana");
        Response listAfter = get(ORDERS, ana());
        Integer totalAfter = jsonPath(listAfter, "$.totalElements");
        assertThat(totalAfter).isEqualTo(2);
    }
}
