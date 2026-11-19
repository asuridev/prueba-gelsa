package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class Ord001FlowIT extends OrderFlowSupport {

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
    @DisplayName("FL-ORD-001: alta de un pedido autorizado al momento")
    void createsAuthorizedOrder() {
        purgeMessages(NOTIFICATIONS);
        Instant start = Instant.now().minusSeconds(2);

        Response response = createOrder(ana(), "k-ana-001", orderBody(TOKEN_OK, line(P1, 2), line(P2, 1)));

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        String id = idOf(response);
        assertThat(response.header("Location")).endsWith("/api/v1/orders/" + id);
        assertOrder(response, id, "ana", "authorized", "EUR", "32.25", PAY1, null, null, p1(2), p2(1));
        String createdAt = jsonPath(response, "$.createdAt");
        assertThat(Instant.parse(createdAt)).isBetween(start, Instant.now().plusSeconds(2));

        List<String> charges = stubRequests("POST", "/payments");
        assertThat(charges).hasSize(1);
        assertChargeBody(charges.get(0), id, "ana", "32.25", "EUR", "tok_visa_ok", null);
        assertThat(capturesOf(PAY1)).isZero();

        Response read = getOrder(ana(), id);
        assertThat(read.status()).isEqualTo(200);
        assertOrder(read, id, "ana", "authorized", "EUR", "32.25", PAY1, null, null, p1(2), p2(1));

        assertNoEmails(Duration.ofSeconds(3));

        // Casos borde: la forma del cuerpo se rechaza con 400.
        assertError(createOrder(ana(), "k-ana-001-e1", orderBody(TOKEN_OK)), 400, "VALIDATION_ERROR");
        String fiftyOne = IntStream.range(0, 51).mapToObj(i -> line(P1, 1)).collect(Collectors.joining(","));
        assertError(createOrder(ana(), "k-ana-001-e2", "{\"lines\":[" + fiftyOne + "]," + TOKEN_OK + "}"), 400,
                "VALIDATION_ERROR");
        assertError(createOrder(ana(), "k-ana-001-e3", orderBody(TOKEN_OK, line(P1, 0))), 400, "VALIDATION_ERROR");
        assertError(createOrder(ana(), "k-ana-001-e4", orderBody(TOKEN_OK, line(P1, 100))), 400, "VALIDATION_ERROR");
        Response noBody = exchangeWithKey(HttpMethod.POST, ORDERS, null, ana(), "k-ana-001-e5");
        assertError(noBody, 400, "VALIDATION_ERROR");
        assertThat(charges()).as("los rechazos no llaman a payment").isEqualTo(1);
    }
}
