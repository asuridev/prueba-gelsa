package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class RescueStuckFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: m1 aceptado y dejado en sending con su reloj rancio (replica muerta con el en la mano).
        purgeMessages(EVENTS);
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
        stallInFlight("dispatchQueuedMessages", m1);
    }

    @Test
    @Order(1)
    @DisplayName("FL-RSC-001: dispatchQueuedMessages rescata lo que otra replica dejo a medias en sending")
    void rescuesStuckMessage() {
        // When: pasa un ciclo.
        // 1. failed con stuck-in-sending.
        awaitStatus(m1, "failed");
        Response failed = messageOf(m1);
        String reason = jsonPath(failed, "$.failureReason");
        String failedAt = jsonPath(failed, "$.failedAt");
        assertThat(reason).isEqualTo("stuck-in-sending");
        assertIsInstant(failedAt);

        // 2. Un EmailDeliveryFailed con ese motivo.
        Map<String, Object> event = awaitEvent("EmailDeliveryFailed", m1);
        assertJson(dataOf(event, "EmailDeliveryFailed"),
                emailFailedData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, "stuck-in-sending", failedAt));
        assertThat(eventsFor("EmailDeliveryFailed", m1)).hasSize(1);

        // 3. El rescate nunca reenvia.
        assertNoMailTo("ana@example.com");

        // 4. Ningun mensaje queda en sending sin reloj.
        assertThat(inFlightWithoutClock("dispatchQueuedMessages")).isZero();
    }
}
