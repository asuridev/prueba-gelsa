package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeRepeatedFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-004: la misma petición de cobro otra vez")
    void sameChargeRequestAgain() {
        // Given: cus-1 con un medio guardado m1 y el cobro ch-020 hecho con él.
        String m1 = seedMethod("cus-1");
        gatewayAuthorizes("ch-020");
        Response first = charge("ch-020", "cus-1", "15.00", "EUR", methodSource(m1));
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        String p20 = idOf(first);
        String gatewayPaymentId = jsonPath(first, "$.gatewayPaymentId");
        Map<String, Object> expected = payment("id", p20, "chargeRequestId", "ch-020", "customerRef", "cus-1",
                "amount", 15.0, "currency", "EUR", "status", "authorized", "gatewayPaymentId", gatewayPaymentId,
                "paymentMethodId", m1);
        assertPayment(first, 201, expected);
        assertThat(chargeCalls("ch-020")).isEqualTo(1);
        awaitEvents("PaymentAuthorized", "ch-020", 1);

        // When: se repite exactamente la misma petición.
        Response repeated = charge("ch-020", "cus-1", "15.00", "EUR", methodSource(m1));
        assertThat(repeated.header("Location")).endsWith("/api/v1/payments/" + p20);
        assertPayment(repeated, 201, expected);
        assertThat(chargeCalls("ch-020")).as("la repetición no vuelve a llamar a la pasarela").isEqualTo(1);
        awaitEvents("PaymentAuthorized", "ch-020", 1);
        assertThat(eventsOf("PaymentFailed", "ch-020")).isEmpty();

        // When: se retira m1 y se repite otra vez: la repetición gana a la guarda del medio.
        Response removed = removeMethod(m1, "cus-1");
        assertThat(removed.status()).isEqualTo(204);
        Response afterRemoval = charge("ch-020", "cus-1", "15.00", "EUR", methodSource(m1));
        assertPayment(afterRemoval, 201, expected);

        // When: mismo chargeRequestId con otro importe.
        Response conflict = charge("ch-020", "cus-1", "16.00", "EUR", methodSource(m1));
        assertError(conflict, 409, "CHARGE_REQUEST_CONFLICT");
        assertThat(chargeCalls("ch-020")).isEqualTo(1);

        // When: con token, la fuente se compara por su clase: otro token es la misma petición.
        gatewayAuthorizes("ch-021");
        Response viaToken = charge("ch-021", "cus-1", "10.00", "EUR", tokenSource());
        assertThat(viaToken.status()).as(viaToken.body()).isEqualTo(201);
        String p21 = idOf(viaToken);
        Response otherToken = charge("ch-021", "cus-1", "10.00", "EUR", tokenSource());
        assertThat(otherToken.status()).as(otherToken.body()).isEqualTo(201);
        assertThat(idOf(otherToken)).isEqualTo(p21);
        assertThat(chargeCalls("ch-021")).isEqualTo(1);

        // When: las claves distinguen mayúsculas.
        assertError(readByCharge("CH-020"), 404, "PAYMENT_NOT_FOUND");
    }
}
