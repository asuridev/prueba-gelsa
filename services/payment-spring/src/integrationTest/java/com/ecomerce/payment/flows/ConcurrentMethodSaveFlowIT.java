package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ConcurrentMethodSaveFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-MTH-002: el mismo guardado a la vez")
    void sameSaveAtTheSameTime() {
        gatewaySavesVisa();
        String body = "{\"customerRef\": \"cus-2\", \"paymentToken\": \"tok-m2\"}";

        List<Response> responses = raceOf(2, () -> exchangeWithKey(HttpMethod.POST,
                ROUTE_BASE + "/payment-methods", body, checkoutToken(), "k-m2"));

        long created = responses.stream().filter(r -> r.status() == 201).count();
        assertThat(created).as("al menos una responde 201").isGreaterThanOrEqualTo(1);
        for (Response response : responses) {
            if (response.status() != 201) {
                assertError(response, 409, "IDEMPOTENCY_KEY_IN_PROGRESS");
            }
        }
        if (created == 2) {
            String first = jsonPath(responses.get(0), "$.id");
            String second = jsonPath(responses.get(1), "$.id");
            assertThat(first).isEqualTo(second);
        }

        Response list = listMethods("cus-2");
        assertThat(list.status()).isEqualTo(200);
        List<Object> items = jsonPath(list, "$");
        assertThat(items).hasSize(1);
        assertThat(attachCalls()).isEqualTo(1);
    }
}
