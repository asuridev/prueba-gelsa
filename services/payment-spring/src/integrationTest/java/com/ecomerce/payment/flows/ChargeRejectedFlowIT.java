package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeRejectedFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-006: las peticiones de cobro que se rechazan")
    void chargeRequestsThatAreRejected() {
        // Given: cus-1 con un medio m1 y otro m9 ya retirado.
        String m1 = seedMethod("cus-1");
        String m9 = seedMethod("cus-1");
        assertThat(removeMethod(m9, "cus-1").status()).isEqualTo(204);

        String token = tokenSource();
        Response noSource = charge("ch-040", "cus-1", "10.00", "EUR", null);
        Response bothSources = charge("ch-041", "cus-1", "10.00", "EUR", token + ", " + methodSource(m1));
        Response unknownCurrency = charge("ch-042", "cus-1", "10.00", "ZZZ", tokenSource());
        Response badScaleEur = charge("ch-043", "cus-1", "10.555", "EUR", tokenSource());
        Response badScaleJpy = charge("ch-044", "cus-1", "10.5", "JPY", tokenSource());
        Response otherOwner = charge("ch-045", "cus-2", "10.00", "EUR", methodSource(m1));
        Response removedMethod = charge("ch-046", "cus-1", "10.00", "EUR", methodSource(m9));
        Response missingMethod = charge("ch-047", "cus-1", "10.00", "EUR", methodSource(UUID.randomUUID().toString()));
        gatewayAuthorizes("ch-048");
        Response kuwaiti = charge("ch-048", "cus-1", "1.234", "KWD", tokenSource());
        Response sourceBeforeCurrency = charge("ch-049", "cus-1", "10.00", "ZZZ", null);

        assertError(noSource, 400, "PAYMENT_SOURCE_INVALID");
        assertError(bothSources, 400, "PAYMENT_SOURCE_INVALID");
        assertError(unknownCurrency, 400, "CURRENCY_UNKNOWN");
        assertError(badScaleEur, 400, "AMOUNT_SCALE_INVALID");
        assertError(badScaleJpy, 400, "AMOUNT_SCALE_INVALID");
        assertError(otherOwner, 422, "PAYMENT_METHOD_UNAVAILABLE");
        assertError(removedMethod, 422, "PAYMENT_METHOD_UNAVAILABLE");
        assertError(missingMethod, 422, "PAYMENT_METHOD_UNAVAILABLE");
        assertThat(kuwaiti.status()).as(kuwaiti.body()).isEqualTo(201);
        String kuwaitiStatus = jsonPath(kuwaiti, "$.status");
        assertThat(kuwaitiStatus).isEqualTo("authorized");
        assertError(sourceBeforeCurrency, 400, "PAYMENT_SOURCE_INVALID");

        // Salvo el de KWD, la pasarela no recibió ningún cobro y no hay cobro registrado.
        for (String ref : List.of("ch-040", "ch-041", "ch-042", "ch-043", "ch-044", "ch-045", "ch-046", "ch-047",
                "ch-049")) {
            assertThat(chargeCalls(ref)).as("cobros de %s en la pasarela", ref).isZero();
            assertError(readByCharge(ref), 404, "PAYMENT_NOT_FOUND");
        }

        // Casos borde de forma: todos 400.
        assertThat(charge("ch bad", "cus-1", "10.00", "EUR", tokenSource()).status()).isEqualTo(400);
        assertThat(charge("x".repeat(101), "cus-1", "10.00", "EUR", tokenSource()).status()).isEqualTo(400);
        assertThat(charge("ch-edge-a", "cus-1", "0", "EUR", tokenSource()).status()).isEqualTo(400);
        assertThat(charge("ch-edge-b", "cus-1", "-5.00", "EUR", tokenSource()).status()).isEqualTo(400);
        Response noCustomer = post(ROUTE_BASE + "/payments", "{\"chargeRequestId\": \"ch-edge-c\", \"amount\": 10.00, "
                + "\"currency\": \"EUR\", \"paymentToken\": \"tok_edge\"}", checkoutToken());
        assertThat(noCustomer.status()).as(noCustomer.body()).isEqualTo(400);
    }
}
