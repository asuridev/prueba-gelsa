package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class RescueFreshFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // La ventana del canal se abre antes de la accion: el Then afirma que no se publica nada.
        purgeMessages(EVENTS);
        // Given: m1 aceptado y dejado en sending con sendingSince = ahora, antes de que corra ningun ciclo.
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
        putInFlight("dispatchQueuedMessages", m1);
    }

    @Test
    @Order(1)
    @DisplayName("FL-RSC-002: dispatchQueuedMessages no rescata lo que acaba de entrar en sending")
    void doesNotRescueFreshSending() {
        Response before = messageOf(m1);
        String statusBefore = jsonPath(before, "$.status");
        String sendingSince = jsonPath(before, "$.sendingSince");
        assertThat(statusBefore).isEqualTo("sending");
        assertIsInstant(sendingSince);

        // When: pasan dos ciclos del despacho.
        // 1. Sigue sending con el mismo sendingSince.
        holdsFor(Duration.ofSeconds(140), () -> "sending".equals(statusOf(m1)));
        Response after = messageOf(m1);
        String sendingSinceAfter = jsonPath(after, "$.sendingSince");
        assertThat(sendingSinceAfter).isEqualTo(sendingSince);

        // Ni evento ni correo. Leer el canal ya prueba que existe (la lectura falla si no):
        // la ausencia no es la de un canal roto.
        assertThat(eventsOfType("EmailDeliveryFailed")).isEmpty();
        assertThat(eventsOfType("EmailSent")).isEmpty();
        assertThat(mailCount("ana@example.com")).isZero();
    }
}
