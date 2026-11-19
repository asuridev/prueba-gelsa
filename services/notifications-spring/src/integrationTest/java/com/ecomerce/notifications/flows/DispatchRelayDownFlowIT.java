package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * FL-DSP-022: con el relay caído (no acepta conexiones, no es un rechazo) el mensaje falla con
 * delivery-error y no se reintenta cuando el relay vuelve.
 */
class DispatchRelayDownFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-022: con el relay caido el mensaje falla y no se reintenta")
    void relayDownFailsWithoutRetry() {
        // La ventana de eventos se abre aqui: la preparacion no publica, pero el Then cuenta.
        purgeMessages(EVENTS);
        String m1;
        String failedAt;
        // Given: relay indisponible (contenedor parado: el intento falla sin respuesta del relay).
        stopMailRelay();
        try {
            // Given: billing pide R1 -> m1.
            m1 = requestAccepted("billing", r1("inv-2026-0001"));

            // When: pasa un ciclo del despacho.
            // 1. m1 queda failed con delivery-error...
            awaitStatus(m1, "failed");
            Response failed = messageOf(m1);
            assertStatus(failed, 200);
            String reason = jsonPath(failed, "$.failureReason");
            failedAt = jsonPath(failed, "$.failedAt");
            Object sentAt = jsonPath(failed, "$.sentAt");
            assertThat(reason).isEqualTo("delivery-error");
            assertIsInstant(failedAt);
            assertThat(sentAt).isNull();

            // ... y se publica EmailDeliveryFailed con su data completo.
            Map<String, Object> event = awaitEvent("EmailDeliveryFailed", m1);
            assertJson(dataOf(event, "EmailDeliveryFailed"),
                    emailFailedData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, "delivery-error", failedAt));
        } finally {
            // Siempre: un relay caido envenena los flujos siguientes. Vuelve con el buzon vacio.
            startMailRelay();
        }

        // When: el relay vuelve y pasan dos ciclos mas.
        // 2. m1 sigue failed y el buzon no recibe su correo: no hay reintento.
        String messageId = m1;
        holdsFor(Duration.ofSeconds(140), () -> "failed".equals(statusOf(messageId))
                && mailCount("ana@example.com") == 0
                && mailCount("luis@example.com") == 0);
        Response after = messageOf(m1);
        String reasonAfter = jsonPath(after, "$.failureReason");
        String failedAtAfter = jsonPath(after, "$.failedAt");
        assertThat(reasonAfter).isEqualTo("delivery-error");
        assertThat(failedAtAfter).isEqualTo(failedAt);
        // Un solo desenlace publicado: ni un segundo EmailDeliveryFailed ni un EmailSent tardio.
        assertThat(eventsFor("EmailDeliveryFailed", m1)).hasSize(1);
        assertThat(eventsFor("EmailSent", m1)).isEmpty();
    }
}
