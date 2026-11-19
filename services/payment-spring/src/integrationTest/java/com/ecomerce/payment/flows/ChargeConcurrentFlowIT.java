package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ChargeConcurrentFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CHG-005: la misma petición de cobro a la vez")
    void sameChargeRequestAtTheSameTime() {
        gatewayAuthorizes("ch-030");
        String body = "{\"chargeRequestId\": \"ch-030\", \"customerRef\": \"cus-5\", \"amount\": 9.99, "
                + "\"currency\": \"EUR\", \"paymentToken\": \"tok_concurrent_030\"}";

        List<Response> responses = raceOf(2, () -> post(ROUTE_BASE + "/payments", body, checkoutToken()));

        for (Response response : responses) {
            assertThat(response.status()).as(response.body()).isEqualTo(201);
        }
        String first = jsonPath(responses.get(0), "$.id");
        String second = jsonPath(responses.get(1), "$.id");
        assertThat(first).isEqualTo(second);

        Response list = listPayments("customerRef=cus-5");
        assertThat(list.status()).isEqualTo(200);
        Number total = jsonPath(list, "$.totalElements");
        assertThat(total.longValue()).isEqualTo(1L);
        assertThat(chargeCalls("ch-030")).isEqualTo(1);

        // Exactamente un desenlace para ch-030.
        awaitEvents("PaymentAuthorized", "ch-030", 1);
        assertThat(eventsOf("PaymentFailed", "ch-030")).isEmpty();
        assertThat(eventsOf("PaymentActionRequired", "ch-030")).isEmpty();
    }
}
