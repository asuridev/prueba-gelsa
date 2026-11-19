package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord005FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        // Más que el timeoutMs (5000) declarado para requestCharge.
        stubTimeout("POST", "/payments", 6500);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-005: payment no contesta, el pedido queda pendiente")
    void paymentDoesNotAnswer() {
        purgeMessages(NOTIFICATIONS);

        Response response = createOrder(ana(), "k-ana-005", orderBody(TOKEN_OK, line(P1, 1)));

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        String id = idOf(response);
        assertThat(response.header("Location")).endsWith("/api/v1/orders/" + id);
        assertOrder(response, id, "ana", "pendingPayment", "EUR", "12.50", null, null, null, p1(1));

        List<String> charges = stubRequests("POST", "/payments");
        assertThat(charges).as("tres intentos de la misma petición").hasSize(3);
        for (String charge : charges) {
            assertChargeBody(charge, id, "ana", "12.50", "EUR", "tok_visa_ok", null);
        }
        assertNoEmails(Duration.ofSeconds(3));

        // Ramas condicionales: credencial M2M rechazada -> mismo resultado.
        stubFailure("POST", "/payments", 401);
        Response unauthorized = createOrder(ana(), "k-ana-005-401", orderBody(TOKEN_OK, line(P1, 1)));
        assertThat(unauthorized.status()).as(unauthorized.body()).isEqualTo(201);
        String status401 = jsonPath(unauthorized, "$.status");
        assertThat(status401).isEqualTo("pendingPayment");

        stubFailure("POST", "/payments", 403);
        Response forbidden = createOrder(ana(), "k-ana-005-403", orderBody(TOKEN_OK, line(P1, 1)));
        assertThat(forbidden.status()).as(forbidden.body()).isEqualTo(201);
        String status403 = jsonPath(forbidden, "$.status");
        assertThat(status403).isEqualTo("pendingPayment");
    }
}
