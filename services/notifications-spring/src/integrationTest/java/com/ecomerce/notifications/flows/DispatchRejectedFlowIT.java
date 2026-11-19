package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchRejectedFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: R1 con destinatarios que el relay rechaza (el dominio .invalid se rechaza siempre).
        m1 = requestAccepted("billing", r1To("inv-2026-0001", rejectedAddress("ana"), rejectedAddress("luis")));
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-020: el relay rechaza a todos los destinatarios")
    void relayRejectsEveryRecipient() {
        // When: pasa un ciclo.
        awaitStatus(m1, "failed");

        // 1. failed con delivery-rejected, habiendo pasado por sending.
        Response failed = messageOf(m1);
        String reason = jsonPath(failed, "$.failureReason");
        String sendingSince = jsonPath(failed, "$.sendingSince");
        String failedAt = jsonPath(failed, "$.failedAt");
        assertThat(reason).isEqualTo("delivery-rejected");
        assertIsInstant(sendingSince);
        assertIsInstant(failedAt);

        // 2. Un EmailDeliveryFailed con ese motivo.
        Map<String, Object> event = awaitEvent("EmailDeliveryFailed", m1);
        assertJson(dataOf(event, "EmailDeliveryFailed"),
                emailFailedData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, "delivery-rejected", failedAt));

        // 3. Dos ciclos despues sigue failed, sin segundo intento ni segundo evento.
        holdsFor(Duration.ofSeconds(140), () -> "failed".equals(statusOf(m1)));
        assertThat(eventsFor("EmailDeliveryFailed", m1)).hasSize(1);
        assertThat(eventsOfType("EmailSent")).isEmpty();
    }
}
