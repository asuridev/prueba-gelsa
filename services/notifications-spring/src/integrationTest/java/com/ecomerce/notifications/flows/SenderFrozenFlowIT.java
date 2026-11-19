package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SenderFrozenFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SND-003: el mensaje conserva el remitente congelado al aceptarlo")
    void messageKeepsFrozenSender() {
        // Given: m1 aceptado con el remitente original congelado.
        Response accepted = requestAs("billing", r1("inv-2026-0001"));
        assertStatus(accepted, 202);
        String m1 = jsonPath(accepted, "$.id");
        Object frozen = jsonPath(accepted, "$.sender");
        assertJson(toJson(frozen), """
            {"address": "avisos@notify.example.com", "name": "Acme Avisos", "replyToAddress": "soporte@example.com"}""");

        // When: cambia el remitente, se pide m2 y pasan dos ciclos.
        Response changed = put(SENDER_PATH, "{\"senderAddress\": \"nuevo@notify.example.com\"}", tokenFor(ADMIN));
        assertStatus(changed, 200);
        String m2 = requestAccepted("billing", r1("inv-2026-0002"));

        // Then 1: los dos quedan sent.
        awaitStatus(m1, "sent");
        awaitStatus(m2, "sent");

        // Then 2: m1 conserva el sender original, en el API y en el correo.
        Response m1Body = messageOf(m1);
        Object m1Sender = jsonPath(m1Body, "$.sender");
        assertJson(toJson(m1Sender), """
            {"address": "avisos@notify.example.com", "name": "Acme Avisos", "replyToAddress": "soporte@example.com"}""");
        List<Map<String, Object>> mails = awaitMails("ana@example.com", 2);
        Map<String, Object> mail1 = mailOfMessage(mails, m1).orElseThrow(() -> new AssertionError("sin correo de m1"));
        assertThat(mailFrom(mail1)).isEqualTo("avisos@notify.example.com");
        assertThat(fromName(mail1)).isEqualTo("Acme Avisos");
        assertThat(addressesOf(mail1, "ReplyTo")).containsExactly("soporte@example.com");

        // Then 3: m2 con el sender nuevo y sin Reply-To.
        Response m2Body = messageOf(m2);
        Object m2Sender = jsonPath(m2Body, "$.sender");
        assertJson(toJson(m2Sender), """
            {"address": "nuevo@notify.example.com", "name": null, "replyToAddress": null}""");
        Map<String, Object> mail2 = mailOfMessage(mails, m2).orElseThrow(() -> new AssertionError("sin correo de m2"));
        assertThat(mailFrom(mail2)).isEqualTo("nuevo@notify.example.com");
        assertThat(addressesOf(mail2, "ReplyTo")).isEmpty();
    }
}
