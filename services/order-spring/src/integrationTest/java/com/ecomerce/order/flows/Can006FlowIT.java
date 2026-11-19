package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can006FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/10\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            stubForDelayed("POST", "/payments/" + PAY1 + "/cancel", 200,
                    "{\"id\":\"" + PAY1 + "\",\"status\":\"canceling\",\"failureReason\":null}", 300);
            o1 = idOf(createdOrder(ana(), "k-ana-c06", orderBody(TOKEN_OK, line(P1, 1))));
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-006: dos anulaciones del mismo pedido a la vez")
    void twoCancellationsAtTheSameTime() {
        assertThat(ana()).isNotBlank();

        List<Response> results = raceOf(2, () -> cancelOrder(ana(), o1));

        Response a = results.get(0);
        Response b = results.get(1);
        if (a.status() == 200 && b.status() == 200) {
            String statusA = jsonPath(a, "$.status");
            String statusB = jsonPath(b, "$.status");
            assertThat(statusA).isEqualTo("canceling");
            assertThat(statusB).isEqualTo("canceling");
        } else {
            Response ok = a.status() == 200 ? a : b;
            Response rejected = a.status() == 200 ? b : a;
            assertThat(ok.status()).as(a.body() + " / " + b.body()).isEqualTo(200);
            assertError(rejected, 409, "CONCURRENT_MODIFICATION");
        }
        holdsFor(Duration.ofSeconds(3), () -> cancelsOf(PAY1) == 1);
        assertThat(statusAssertedAna(o1)).isEqualTo("canceling");
    }
}
