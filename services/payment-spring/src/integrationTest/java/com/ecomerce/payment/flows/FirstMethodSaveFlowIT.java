package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class FirstMethodSaveFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-MTH-001: se guarda el primer medio de un titular, y su repetición")
    void savesFirstMethodAndItsRepetition() {
        gatewaySavesCard("visa", "4242", 12, 2030, newPmId());

        Response first = saveMethod("cus-1", "k-m1", "tok-m1", null);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String m1 = jsonPath(first, "$.id");
        String w1 = jsonPath(first, "$.walletId");
        assertIsUuid(m1);
        assertIsUuid(w1);
        Map<String, Object> item = methodItem(m1, "visa", "4242", 12, 2030, true, false, w1);
        assertBody(first, toJson(item));

        Response list = listMethods("cus-1");
        assertThat(list.status()).isEqualTo(200);
        assertBody(list, toJson(List.of(item)));

        // Misma Idempotency-Key y mismo cuerpo: la misma respuesta, sin un segundo guardado.
        Response repeated = saveMethod("cus-1", "k-m1", "tok-m1", null);
        assertThat(repeated.status()).as(repeated.body()).isEqualTo(201);
        assertBody(repeated, toJson(item));
        assertThat(attachCalls()).isEqualTo(1);
        assertBody(listMethods("cus-1"), toJson(List.of(item)));

        // Misma clave, otro cuerpo.
        Response reused = saveMethod("cus-1", "k-m1", "tok-m1", "\"makeDefault\": true");
        assertError(reused, 409, "IDEMPOTENCY_KEY_REUSED");
        assertThat(attachCalls()).isEqualTo(1);

        // Sin cabecera Idempotency-Key.
        Response noKey = exchangeWithoutIdempotencyKey(HttpMethod.POST, ROUTE_BASE + "/payment-methods",
                "{\"customerRef\": \"cus-1\", \"paymentToken\": \"tok-nokey\"}", checkoutToken());
        assertError(noKey, 400, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(attachCalls()).isEqualTo(1);

        // Casos borde de forma: customerRef vacío y paymentToken ausente.
        Response emptyCustomer = saveMethod("", "k-m1-edge-a", "tok-edge", null);
        assertThat(emptyCustomer.status()).as(emptyCustomer.body()).isEqualTo(400);
        Response noToken = exchangeWithKey(HttpMethod.POST, ROUTE_BASE + "/payment-methods",
                "{\"customerRef\": \"cus-1\"}", checkoutToken(), "k-m1-edge-b");
        assertThat(noToken.status()).as(noToken.body()).isEqualTo(400);
        assertThat(attachCalls()).isEqualTo(1);
    }
}
