package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class EventRequestFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    private int totalMessages() {
        return totalElements(get(MESSAGES_PATH, tokenFor(ADMIN)));
    }

    @Test
    @Order(1)
    @DisplayName("FL-EVT-001: pedir un correo por el canal de eventos, y su reentrega")
    void requestByEventAndRedelivery() {
        String e1 = UUID.randomUUID().toString();

        // When (1): llega NotificationRequested de billing con data = R1.
        deliverNotificationRequested(e1, "billing", r1("inv-2026-0001"));

        // 1. El mensaje existe, a nombre de billing, queued o posterior.
        await(Duration.ofSeconds(30), () -> findByKey("billing", "inv-2026-0001").status() == 200);
        Response found = findByKey("billing", "inv-2026-0001");
        String m1 = jsonPath(found, "$.id");
        String requestedBy = jsonPath(found, "$.requestedBy");
        String status = jsonPath(found, "$.status");
        assertThat(requestedBy).isEqualTo("billing");
        assertThat(status).isIn("queued", "sending", "sent");

        // 2. Tras un ciclo: correo en el buzon y un EmailSent.
        awaitStatus(m1, "sent");
        awaitMailOfMessage("ana@example.com", m1);
        awaitEvent("EmailSent", m1);

        // When (2): reentrega con el MISMO eventId.
        deliverNotificationRequested(e1, "billing", r1("inv-2026-0001"));

        // 3. No hay otro mensaje ni segundo correo, ni acaba en la deadLetter.
        holdsFor(Duration.ofSeconds(20), () -> totalMessages() == 1 && mailCount("ana@example.com") == 1);
        assertThat(deadLetterMessages("NotificationRequested", 5)).isBlank();

        // When (3): la misma clave por HTTP es el mismo envio.
        Response http = requestAs("billing", r1("inv-2026-0001"));
        assertStatus(http, 202);
        String httpId = jsonPath(http, "$.id");
        assertThat(httpId).isEqualTo(m1);
    }
}
