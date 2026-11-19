package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchDrainFlowIT extends NotificationsFlowBase {

    private final List<String> ids = new ArrayList<>();

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pide tres notificaciones (202 por contrato).
        for (String key : List.of("key-0001", "key-0002", "key-0003")) {
            ids.add(requestAccepted("billing", r1(key)));
        }
    }

    @BeforeEach
    void awaitDispatch() {
        awaitPreconditions(() -> {
            for (String id : ids) {
                awaitStatus(id, "sent");
            }
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-001: el despacho vacia la cola")
    void dispatchDrainsQueue() {
        // 1. Los tres sent, tres correos y tres EmailSent.
        for (String id : ids) {
            assertThat(statusOf(id)).isEqualTo("sent");
        }
        awaitMailTo("ana@example.com", 3);
        for (String id : ids) {
            awaitEvent("EmailSent", id);
        }
        assertThat(eventsOfType("EmailSent")).hasSize(3);

        // 2. Otro ciclo no envia nada nuevo.
        holdsFor(Duration.ofSeconds(75), () -> mailCount("ana@example.com") == 3);
        assertThat(eventsOfType("EmailSent")).hasSize(3);
    }
}
