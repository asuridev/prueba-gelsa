package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SuppressionConcurrentFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedSender();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SUP-002: dos supresiones de la misma direccion a la vez")
    void twoSuppressionsAtTheSameTime() {
        String path = SUPPRESSIONS_PATH + "/x@example.com";
        List<Response> results = race(List.of(
                () -> put(path, "{\"reason\": \"manual\"}", tokenFor(OPERATOR)),
                () -> put(path, "{\"reason\": \"manual\"}", tokenFor(ADMIN))));

        // 1. Desenlaces admisibles: 200+200, o 200 + 409 (ya existe / version modificada).
        int ok = 0;
        for (Response result : results) {
            if (result.status() == 200) {
                ok++;
            } else {
                assertThat(result.status()).isEqualTo(409);
                String code = jsonPath(result, "$.code");
                assertThat(code).isIn("SUPPRESSED_ADDRESS_ALREADY_EXISTS", "CONCURRENT_MODIFICATION");
            }
        }
        assertThat(ok).isGreaterThanOrEqualTo(1);

        // 2. Un unico registro active.
        Response record = get(path, tokenFor(OPERATOR));
        assertStatus(record, 200);
        String status = jsonPath(record, "$.status");
        assertThat(status).isEqualTo("active");
        Response list = get(SUPPRESSIONS_PATH, tokenFor(OPERATOR));
        assertThat(totalElements(list)).isEqualTo(1);
    }
}
