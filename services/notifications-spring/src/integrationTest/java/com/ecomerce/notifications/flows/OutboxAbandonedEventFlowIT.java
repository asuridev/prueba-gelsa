package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class OutboxAbandonedEventFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-OBX-002: el evento que el relay de eventos abandona no se pierde en silencio")
    void abandonedEventIsReported() {
        purgeMessages(EVENTS);
        stopBroker();
        try {
            awaitBrokerStopped();

            // Given: m1 sent con su EmailSent pendiente de salir (canal indisponible).
            String m1 = requestAccepted("billing", r1("inv-2026-0001"));
            awaitStatus(m1, "sent");
            assertThat(deadLetteredEvents()).isZero();

            // When: se agota el presupuesto de reintentos de ese evento.
            abandonOutboxEvent("EmailSent");

            // 1. El servidor informa de UN evento abandonado.
            await(Duration.ofSeconds(30), () -> deadLetteredEvents() == 1);
            assertThat(deadLetteredEvents()).isEqualTo(1);
        } finally {
            startBroker();
        }

        try {
            // 2 y 3. Restablecido el canal, ese EmailSent no se publica y el canal no recibe otra cosa.
            holdsFor(Duration.ofSeconds(30), () -> publishedMessages(EVENTS, 10).trim().equals("[]"));
            assertThat(deadLetteredEvents()).isEqualTo(1);
        } finally {
            clearAbandonedOutboxEvents();
        }
    }
}
