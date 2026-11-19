package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class InFlightRescueFlowIT extends PaymentFlowSupport {

    private static final List<String> WAITING = List.of("pending", "actionRequired", "capturing", "canceling",
            "refunding");

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-004: se rescata lo que otra réplica dejó en vuelo, y lo reciente no se toca")
    void rescuesWhatAnotherReplicaLeftInFlight() {
        // Given: dos cobros en capturing sin respuesta de la pasarela, que sí capturó los dos.
        String p330 = seedAuthorized("ch-330");
        String p331 = seedAuthorized("ch-331");
        gatewayDoesNotAnswer(GatewayCall.CAPTURE);
        assertThat(capture(p330).status()).isEqualTo(200);
        assertThat(capture(p331).status()).isEqualTo(200);
        assertThat(statusOf(p330)).isEqualTo("capturing");
        assertThat(statusOf(p331)).isEqualTo("capturing");
        gatewayReports("ch-330", "CAPTURED");
        gatewayReports("ch-331", "CAPTURED");

        // ch-330 con el reloj más atrás que el umbral; ch-331 con el reloj reciente (se retiene del barrido).
        ageAwaiting(p330);
        holdFromReconciliation("sweepPendingPayments", p331);
        Map<String, Object> before331 = JsonPath.read(readPayment(p331).body(), "$");

        // When: pasan dos ciclos.
        runSweep();
        awaitStatus(p330, "captured");
        runSweep();
        holdsFor(Duration.ofSeconds(15), () -> statusQueries("ch-331") == 0 && "capturing".equals(statusOf(p331)));

        // Then.
        assertThat(statusQueries("ch-330")).as("consulta de estado de ch-330").isGreaterThanOrEqualTo(1);
        assertThat(statusQueries("ch-331")).as("ninguna consulta de ch-331").isZero();
        awaitEvents("PaymentCaptured", "ch-330", 1);
        assertPayment(readPayment(p331), 200, payment("id", p331, "chargeRequestId", "ch-331", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "capturing",
                "gatewayPaymentId", before331.get("gatewayPaymentId"),
                "awaitingSince", before331.get("awaitingSince")));

        // Ningún cobro queda en un estado de espera con awaitingSince nulo.
        Response all = listPayments("customerRef=cus-1&page=0&size=100");
        List<Map<String, Object>> items = JsonPath.read(all.body(), "$.items");
        for (Map<String, Object> item : items) {
            if (WAITING.contains((String) item.get("status"))) {
                assertThat(item.get("awaitingSince")).as("awaitingSince de %s", item.get("chargeRequestId"))
                        .isNotNull();
            }
        }
    }
}
