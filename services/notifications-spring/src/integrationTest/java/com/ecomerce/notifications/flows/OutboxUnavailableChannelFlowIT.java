package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class OutboxUnavailableChannelFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-OBX-001: el evento sobrevive a un canal indisponible")
    void eventSurvivesUnavailableChannel() {
        // Given: canal notificationEvents sin mensajes (la ventana empieza aqui) y el broker caido.
        purgeMessages(EVENTS);
        String m1;
        String sentAt;
        stopBroker();
        try {
            awaitBrokerStopped();

            // When: billing pide R1 y pasa un ciclo.
            m1 = requestAccepted("billing", r1("inv-2026-0001"));

            // 1. m1 queda sent y el buzon recibe su correo.
            awaitStatus(m1, "sent");
            awaitMailOfMessage("ana@example.com", m1);
            Response sent = messageOf(m1);
            sentAt = jsonPath(sent, "$.sentAt");

            // 2. El canal no ha recibido nada todavia.
            assertThat(publishedMessages(EVENTS, 10).trim()).isEqualTo("[]");
        } finally {
            startBroker();
        }

        // When: el canal vuelve. 3. Exactamente un EmailSent de m1 con su data completo.
        Map<String, Object> event = awaitEventWithin(m1, Duration.ofSeconds(60));
        assertJson(dataOf(event, "EmailSent"), emailSentData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, sentAt));
        holdsFor(Duration.ofSeconds(10), () -> eventsFor("EmailSent", m1).size() == 1);

        // 4. El relay no abandono ningun evento.
        assertThat(deadLetteredEvents()).isZero();
    }

    private Map<String, Object> awaitEventWithin(String messageId, Duration timeout) {
        await(timeout, () -> !eventsFor("EmailSent", messageId).isEmpty());
        return eventsFor("EmailSent", messageId).get(0);
    }
}
