package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchPartialRejectionFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: el relay rechaza a luis (TLD .invalid) y acepta a ana.
        m1 = requestAccepted("billing", r1To("inv-2026-0001", "ana@example.com", rejectedAddress("luis")));
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-021: el relay rechaza a uno y acepta al otro")
    void relayRejectsOneAcceptsTheOther() {
        // When: pasa un ciclo.
        // 1. sent: el correo salio para al menos un destinatario.
        awaitStatus(m1, "sent");

        // 2. El buzon recibe el correo para ana y se publica un EmailSent.
        Map<String, Object> mail = awaitMailOfMessage("ana@example.com", m1);
        assertThat(messageIdLocalPart(mail)).isEqualTo(m1);
        Response sent = messageOf(m1);
        String sentAt = jsonPath(sent, "$.sentAt");
        Map<String, Object> event = awaitEvent("EmailSent", m1);
        assertJson(dataOf(event, "EmailSent"), emailSentData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, sentAt));
        assertThat(eventsFor("EmailDeliveryFailed", m1)).isEmpty();
    }
}
