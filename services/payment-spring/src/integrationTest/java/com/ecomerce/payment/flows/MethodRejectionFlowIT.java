package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class MethodRejectionFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-MTH-004: lo que no se puede guardar")
    void whatCannotBeSaved() {
        // Given: cus-3 con 10 medios active, cada uno con su propia Idempotency-Key.
        for (int i = 0; i < 10; i++) {
            seedMethod("cus-3");
        }
        Response tenth = listMethods("cus-3");
        List<Object> saved = jsonPath(tenth, "$");
        assertThat(saved).hasSize(10);
        int attachedBefore = attachCalls();
        assertThat(attachedBefore).isEqualTo(10);

        // When: undécimo con la pasarela rechazando. El tope gana: la pasarela no recibe nada.
        gatewayRejects(GatewayCall.ATTACH_METHOD);
        Response limit = saveMethod("cus-3", "k-m4a", "tok-4a", null);
        assertError(limit, 422, "PAYMENT_METHOD_LIMIT_REACHED");
        assertThat(attachCalls()).isEqualTo(attachedBefore);

        // When: cus-4 sin medios, tres desenlaces de la pasarela.
        Response rejected = saveMethod("cus-4", "k-m4b", "tok-4b", null);
        assertError(rejected, 422, "PAYMENT_METHOD_REJECTED");

        stubFor("POST", "/v1/payment_methods/[^/]+/attach", 402,
                "{\"error\": {\"type\": \"card_error\", \"code\": \"authentication_required\"}}");
        Response authentication = saveMethod("cus-4", "k-m4c", "tok-4c", null);
        assertError(authentication, 422, "PAYMENT_METHOD_AUTHENTICATION_REQUIRED");

        gatewayDoesNotAnswer(GatewayCall.ATTACH_METHOD);
        Response unavailable = saveMethod("cus-4", "k-m4d", "tok-4d", null);
        assertError(unavailable, 503, "GATEWAY_UNAVAILABLE");

        Response none = listMethods("cus-4");
        assertThat(none.status()).isEqualTo(200);
        assertBody(none, "[]");

        // When: la pasarela vuelve a aceptar y se repite la clave del caso sin respuesta, con otro token.
        gatewaySavesVisa();
        int attachedAfterFailures = attachCalls();
        Response retried = saveMethod("cus-4", "k-m4d", "tok-4e", null);
        assertThat(retried.status()).as(retried.body()).isEqualTo(201);
        String retriedId = idOf(retried);
        assertThat(attachCalls()).isEqualTo(attachedAfterFailures + 1);

        Response one = listMethods("cus-4");
        List<Object> items = jsonPath(one, "$");
        assertThat(items).hasSize(1);
        String listedId = jsonPath(one, "$[0].id");
        assertThat(listedId).isEqualTo(retriedId);
    }
}
