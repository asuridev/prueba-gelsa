package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchSuppressedFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: m1 queued y, antes del siguiente ciclo, operator suprime a luis.
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
        suppress("luis@example.com", "{\"reason\": \"manual\"}");
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-010: una direccion suprimida despues de aceptar no recibe el correo")
    void suppressedAfterAcceptanceIsNotSent() {
        // When: pasa un ciclo del despacho.
        awaitStatus(m1, "failed");

        // 1. failed por supresion, sin haber pasado por sending.
        Response failed = messageOf(m1);
        String reason = jsonPath(failed, "$.failureReason");
        String failedAt = jsonPath(failed, "$.failedAt");
        Object sendingSince = jsonPath(failed, "$.sendingSince");
        assertThat(reason).isEqualTo("recipient-suppressed");
        assertIsInstant(failedAt);
        assertThat(sendingSince).isNull();

        // 3. Un EmailDeliveryFailed con su data completo.
        Map<String, Object> event = awaitEvent("EmailDeliveryFailed", m1);
        assertJson(dataOf(event, "EmailDeliveryFailed"),
                emailFailedData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, "recipient-suppressed", failedAt));
        assertThat(eventsFor("EmailDeliveryFailed", m1)).hasSize(1);

        // 2. No sale correo de m1, tampoco a ana.
        assertNoMailTo("ana@example.com");
        assertNoMailTo("luis@example.com");
    }
}
