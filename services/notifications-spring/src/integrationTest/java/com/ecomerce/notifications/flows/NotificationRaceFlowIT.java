package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationRaceFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-003: dos peticiones con la misma clave a la vez")
    void sameKeyAtTheSameTime() {
        String body = r1("inv-2026-0001");
        List<Response> results = raceOf(2, () -> requestAs("billing", body));

        // 1. Desenlaces admisibles: 202+202 con el mismo id, o 202+409 IDEMPOTENCY_KEY_IN_PROGRESS.
        List<String> ids = new java.util.ArrayList<>();
        for (Response result : results) {
            if (result.status() == 202) {
                String id = jsonPath(result, "$.id");
                ids.add(id);
            } else {
                assertError(result, 409, "IDEMPOTENCY_KEY_IN_PROGRESS");
            }
        }
        assertThat(ids).isNotEmpty();
        assertThat(ids.stream().distinct().count()).isEqualTo(1);

        // 2. Exactamente un mensaje.
        Response all = get(MESSAGES_PATH, tokenFor(ADMIN));
        assertThat(totalElements(all)).isEqualTo(1);

        // 3. Tras dos ciclos, exactamente un correo con esa parte local de Message-ID.
        String id = ids.get(0);
        awaitStatus(id, "sent");
        awaitMailOfMessage("ana@example.com", id);
        holdsFor(Duration.ofSeconds(75), () -> mailCount("ana@example.com") == 1);
        assertThat(mailsOfMessage("ana@example.com", id)).isEqualTo(1);
    }
}
