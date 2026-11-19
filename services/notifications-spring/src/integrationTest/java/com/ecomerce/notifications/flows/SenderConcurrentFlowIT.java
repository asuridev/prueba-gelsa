package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SenderConcurrentFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SND-002: dos primeras configuraciones a la vez dejan un solo remitente")
    void twoFirstConfigurationsLeaveOneSender() {
        List<Response> results = race(List.of(
                () -> put(SENDER_PATH, "{\"senderAddress\": \"a@notify.example.com\"}", tokenFor(ADMIN)),
                () -> put(SENDER_PATH, "{\"senderAddress\": \"b@notify.example.com\"}", tokenFor(ADMIN))));

        // 1. Desenlaces admisibles: 200+200, o 200+409 CONCURRENT_MODIFICATION.
        int ok = 0;
        for (Response result : results) {
            if (result.status() == 200) {
                ok++;
            } else {
                assertError(result, 409, "CONCURRENT_MODIFICATION");
            }
        }
        assertThat(ok).as("al menos una configuracion tiene que ganar").isGreaterThanOrEqualTo(1);

        // 2. Lo que queda es a@ o b@; si una dio 409, es el de la que dio 200.
        Response first = get(SENDER_PATH, tokenFor(ADMIN));
        assertStatus(first, 200);
        String key = jsonPath(first, "$.key");
        String address = jsonPath(first, "$.senderAddress");
        assertThat(key).isEqualTo("default");
        assertThat(address).isIn("a@notify.example.com", "b@notify.example.com");
        if (ok == 1) {
            String winner = results.get(0).status() == 200 ? "a@notify.example.com" : "b@notify.example.com";
            assertThat(address).isEqualTo(winner);
        }

        // 3. Un solo remitente: el id no cambia entre lecturas.
        Response second = get(SENDER_PATH, tokenFor(ADMIN));
        String firstId = jsonPath(first, "$.id");
        String secondId = jsonPath(second, "$.id");
        assertThat(secondId).isEqualTo(firstId);
    }
}
