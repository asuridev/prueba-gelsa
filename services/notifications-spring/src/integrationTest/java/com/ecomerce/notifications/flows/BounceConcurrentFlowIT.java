package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class BounceConcurrentFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
    }

    @BeforeEach
    void awaitSent() {
        awaitPreconditions(() -> awaitStatus(m1, "sent"));
    }

    @Test
    @Order(1)
    @DisplayName("FL-BNC-002: dos rebotes de la misma direccion a la vez")
    void twoBouncesAtTheSameTime() {
        String body = "{\"recipient\": \"luis@example.com\", \"bounceType\": \"permanent\", \"diagnostic\": \"550 user unknown\"}";
        List<Response> results = raceOf(2,
                () -> post(MESSAGES_PATH + "/" + m1 + "/bounces", body, serviceCredential("mail-provider")));

        // 1. Desenlaces admisibles: 204+204, o 204+409 SUPPRESSED_ADDRESS_ALREADY_EXISTS.
        int accepted = 0;
        for (Response result : results) {
            if (result.status() == 204) {
                accepted++;
            } else {
                assertError(result, 409, "SUPPRESSED_ADDRESS_ALREADY_EXISTS");
            }
        }
        assertThat(accepted).isGreaterThanOrEqualTo(1);

        // 2. Un unico registro active.
        Response record = get(SUPPRESSIONS_PATH + "/luis@example.com", tokenFor(OPERATOR));
        assertStatus(record, 200);
        String status = jsonPath(record, "$.status");
        assertThat(status).isEqualTo("active");
        Response list = get(SUPPRESSIONS_PATH, tokenFor(OPERATOR));
        assertThat(totalElements(list)).isEqualTo(1);
    }
}
