package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PaymentNotFoundFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-QRY-002: leer un cobro que no existe")
    void readingAMissingPayment() {
        assertError(readPayment(UUID.randomUUID().toString()), 404, "PAYMENT_NOT_FOUND");
        assertError(readByCharge("ch-999"), 404, "PAYMENT_NOT_FOUND");

        Response malformed = readPayment("no-es-un-uuid");
        assertThat(malformed.status()).as(malformed.body()).isEqualTo(400);
    }
}
