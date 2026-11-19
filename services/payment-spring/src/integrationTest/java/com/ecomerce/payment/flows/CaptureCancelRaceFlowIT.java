package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class CaptureCancelRaceFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-007: dos acciones sobre el mismo cobro a la vez")
    void captureAndCancelAtTheSameTime() {
        String p100 = seedAuthorized("ch-100");
        gatewayCaptures("ch-100");
        gatewayCancels("ch-100");

        List<Callable<Response>> tasks = List.of(() -> capture(p100), () -> cancel(p100));
        List<Response> responses = race(tasks);
        Response captureResponse = responses.get(0);
        Response cancelResponse = responses.get(1);

        // Gana una, nunca un estado mezclado.
        long winners = responses.stream().filter(r -> r.status() == 200).count();
        assertThat(winners).as("captura: %s / anulación: %s", captureResponse.body(), cancelResponse.body())
                .isEqualTo(1L);
        String finalStatus = statusOf(p100);
        assertThat(finalStatus).isIn("captured", "canceled");

        if (captureResponse.status() == 200) {
            assertThat(finalStatus).isEqualTo("captured");
            String code = jsonPath(cancelResponse, "$.code");
            assertThat(cancelResponse.status()).isEqualTo(409);
            assertThat(code).isIn("PAYMENT_NOT_CANCELABLE", "CONCURRENT_MODIFICATION");
        } else {
            assertThat(finalStatus).isEqualTo("canceled");
            String code = jsonPath(captureResponse, "$.code");
            assertThat(captureResponse.status()).isEqualTo(409);
            assertThat(code).isIn("PAYMENT_NOT_CAPTURABLE", "CONCURRENT_MODIFICATION");
        }

        // La pasarela recibió una sola de las dos acciones y sale un solo desenlace.
        assertThat(captureCalls("ch-100") + cancelCalls("ch-100")).isEqualTo(1);
        if ("captured".equals(finalStatus)) {
            awaitEvents("PaymentCaptured", "ch-100", 1);
            assertThat(eventsOf("PaymentCanceled", "ch-100")).isEmpty();
        } else {
            awaitEvents("PaymentCanceled", "ch-100", 1);
            assertThat(eventsOf("PaymentCaptured", "ch-100")).isEmpty();
        }
    }
}
