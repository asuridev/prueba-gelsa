package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NoticeBeforeResponseFlowIT extends PaymentFlowSupport {

    /** Cuánto retiene la pasarela de prueba su respuesta a la captura (el aviso entra en ese intervalo). */
    private static final int HELD_RESPONSE_MS = 4000;

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-009: el aviso adelanta a la respuesta de la captura")
    void noticeOvertakesTheCaptureResponse() {
        // Given: ch-120 autorizado, y una pasarela que retiene la respuesta a la captura.
        String p120 = seedAuthorized("ch-120");
        String id = gatewayIdFor("ch-120");
        String captured = "{\"id\": \"" + id + "\", \"object\": \"payment_intent\", \"currency\": \"eur\", "
                + "\"status\": \"succeeded\", \"metadata\": {\"keel_reference\": \"ch-120\"}, "
                + "\"client_secret\": \"" + id + "_secret_test\"}";
        String held = "{\"status\": 200, \"fixedDelayMilliseconds\": " + HELD_RESPONSE_MS
                + ", \"headers\": {\"Content-Type\": \"application/json\"}, \"body\": " + toJson(captured) + "}";
        stubSequence("POST", "/v1/payment_intents/" + id + "/capture", new StubResponse(held), new StubResponse(held));

        // When: se pide la captura y, en cuanto el cobro está en capturing, llega el aviso de la captura.
        Callable<Response> doCapture = () -> capture(p120);
        Callable<Response> doNotice = () -> {
            await(java.time.Duration.ofSeconds(20), () -> "capturing".equals(statusOf(p120)));
            gatewayReports("ch-120", "CAPTURED");
            return sendGatewayNotice("ch-120");
        };
        List<Response> responses = race(List.of(doCapture, doNotice));
        Response captureResponse = responses.get(0);
        Response noticeResponse = responses.get(1);

        assertThat(noticeResponse.status()).as(noticeResponse.body()).isLessThan(300);
        assertThat(captureResponse.status()).as(captureResponse.body()).isEqualTo(200);
        String status = jsonPath(captureResponse, "$.status");
        assertThat(status).isEqualTo("captured");
        assertThat(statusOf(p120)).isEqualTo("captured");
        awaitEvents("PaymentCaptured", "ch-120", 1);
    }
}
