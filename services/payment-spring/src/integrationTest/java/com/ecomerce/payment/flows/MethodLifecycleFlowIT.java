package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class MethodLifecycleFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-MTH-003: varios medios, el preferente y la retirada")
    void severalMethodsDefaultAndRemoval() {
        // Given: cus-1 con el medio m1 por defecto (como en FL-MTH-001).
        gatewaySavesCard("visa", "4242", 12, 2030, newPmId());
        Response first = saveMethod("cus-1", "k-m1", "tok-m1", null);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String m1 = jsonPath(first, "$.id");
        String w1 = jsonPath(first, "$.walletId");

        // When: segundo medio con makeDefault, tarjeta mastercard 5454 caducada.
        gatewaySavesCard("mastercard", "5454", 1, 2020, newPmId());
        Response second = saveMethod("cus-1", "k-m3", "tok-m3", "\"makeDefault\": true");
        assertThat(second.status()).as(second.body()).isEqualTo(201);
        String m2 = jsonPath(second, "$.id");
        assertIsUuid(m2);
        assertThat(m2).isNotEqualTo(m1);
        assertMethod(second, 201, methodItem(m2, "mastercard", "5454", 1, 2020, true, true, w1));

        Map<String, Object> m1Default = methodItem(m1, "visa", "4242", 12, 2030, true, false, w1);
        Map<String, Object> m1Plain = methodItem(m1, "visa", "4242", 12, 2030, false, false, w1);
        Map<String, Object> m2Default = methodItem(m2, "mastercard", "5454", 1, 2020, true, true, w1);
        Map<String, Object> m2Plain = methodItem(m2, "mastercard", "5454", 1, 2020, false, true, w1);

        Response list = listMethods("cus-1");
        assertThat(list.status()).isEqualTo(200);
        assertBody(list, toJson(List.of(m2Default, m1Plain)));

        // When: setDefault sobre m1, dos veces seguidas.
        Response once = setDefault(m1, "cus-1");
        assertMethod(once, 200, m1Default);
        Response twice = setDefault(m1, "cus-1");
        assertMethod(twice, 200, m1Default);
        assertBody(listMethods("cus-1"), toJson(List.of(m1Default, m2Plain)));

        // When: retirar m1, dos veces.
        Response removed = removeMethod(m1, "cus-1");
        assertThat(removed.status()).isEqualTo(204);
        assertThat(removed.body()).isNullOrEmpty();
        Response removedAgain = removeMethod(m1, "cus-1");
        assertThat(removedAgain.status()).isEqualTo(204);
        assertThat(removedAgain.body()).isNullOrEmpty();
        assertBody(listMethods("cus-1"), toJson(List.of(m2Plain)));

        // When: con m1 retirado, los cuatro casos que no se distinguen.
        assertError(setDefault(m1, "cus-1"), 404, "PAYMENT_METHOD_NOT_FOUND");
        assertError(setDefault(m2, "cus-9"), 404, "PAYMENT_METHOD_NOT_FOUND");
        assertError(removeMethod(m2, "cus-9"), 404, "PAYMENT_METHOD_NOT_FOUND");
        assertError(setDefault(UUID.randomUUID().toString(), "cus-1"), 404, "PAYMENT_METHOD_NOT_FOUND");
        assertBody(listMethods("cus-1"), toJson(List.of(m2Plain)));

        // Nota de determinación: un titular sin medios devuelve [].
        Response empty = listMethods("cus-9");
        assertThat(empty.status()).isEqualTo(200);
        assertBody(empty, "[]");
    }
}
