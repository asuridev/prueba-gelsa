package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchClusterFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    private String recipientOf(int n) {
        return "c" + n + "@example.com";
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-040: dos replicas del despacho no mandan dos correos")
    void twoReplicasSendOnce() {
        try {
            // Given: cyc-0000 pedido y ya sent ANTES de arrancar la segunda instancia (fila de control).
            String control = requestAccepted("billing", r1To("cyc-0000", recipientOf(0)));
            awaitStatus(control, "sent");
            awaitMailTo(recipientOf(0), 1);
            awaitEvent("EmailSent", control);

            // Dos instancias vivas; los 5 envios nuevos y la ventana de eventos desde cero.
            startReplica();
            purgeMessages(EVENTS);
            List<String> ids = new ArrayList<>();
            for (int n = 1; n <= 5; n++) {
                ids.add(requestAccepted("billing", r1To("cyc-000" + n, recipientOf(n))));
            }

            // When: pasan dos ciclos en las dos instancias.
            for (String id : ids) {
                awaitStatus(id, "sent");
            }
            holdsFor(Duration.ofSeconds(75), () -> mailCount(recipientOf(1)) == 1);

            // 1. Exactamente un correo por cada uno de los cinco.
            for (int n = 1; n <= 5; n++) {
                assertThat(mailCount(recipientOf(n))).as("correos de cyc-000" + n).isEqualTo(1);
            }
            // 2. Ningun segundo correo de la fila de control.
            assertThat(mailCount(recipientOf(0))).isEqualTo(1);
            // 3. Exactamente cinco EmailSent nuevos.
            for (String id : ids) {
                awaitEvent("EmailSent", id);
            }
            assertThat(eventsOfType("EmailSent")).hasSize(5);
        } finally {
            stopReplica();
        }
    }
}
