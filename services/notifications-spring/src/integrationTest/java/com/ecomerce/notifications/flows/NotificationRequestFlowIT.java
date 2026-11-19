package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationRequestFlowIT extends NotificationsFlowBase {

    private String v1;

    @BeforeAll
    void prepare() {
        resetState();
        v1 = seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-001: pedir un correo, recibirlo y consultarlo")
    void requestReceiveAndQuery() {
        // When: R1 + variable no declarada + destinatario repetido.
        List<Map<String, String>> variables = r1Variables();
        variables.add(var("coupon", "X"));
        String body = requestJson("inv-2026-0001", "invoice-issued",
                List.of("Ana@Example.com", "luis@example.com", "ana@example.com"), variables);
        purgeMessages(EVENTS);
        Response accepted = requestAs("billing", body);

        // 1. 202 con la proyeccion completa y sin variableValues.
        assertStatus(accepted, 202);
        String m1 = jsonPath(accepted, "$.id");
        String requestedAt = jsonPath(accepted, "$.requestedAt");
        assertIsUuid(m1);
        assertIsInstant(requestedAt);
        assertBody(accepted, """
            {"id": "%s", "requestedBy": "billing", "idempotencyKey": "inv-2026-0001",
             "recipients": ["ana@example.com", "luis@example.com"],
             "renderedSubject": "Factura F-0001",
             "sender": {"address": "avisos@notify.example.com", "name": "Acme Avisos", "replyToAddress": "soporte@example.com"},
             "templateCode": "invoice-issued", "templateVersionNumber": 1, "templateVersionId": "%s",
             "status": "queued", "requestedAt": "%s", "sendingSince": null, "sentAt": null, "failedAt": null,
             "failureReason": null, "personalDataPurgedAt": null}""".formatted(m1, v1, requestedAt));

        // 2. La consulta por clave devuelve el mismo cuerpo, salvo estado e instantes del despacho.
        Response found = findByKey("billing", "inv-2026-0001");
        assertStatus(found, 200);
        assertSameExcept(accepted, found, "status", "sendingSince", "sentAt", "failedAt");

        // 4 (se espera el ESTADO antes que el efecto). El despacho lo envia en un ciclo.
        awaitStatus(m1, "sent");

        // 3. Un solo correo con el contenido del contrato.
        Map<String, Object> mail = awaitMailOfMessage("ana@example.com", m1);
        assertThat(mailCount("ana@example.com")).isEqualTo(1);
        assertThat(mailFrom(mail)).isEqualTo("avisos@notify.example.com");
        assertThat(fromName(mail)).isEqualTo("Acme Avisos");
        assertThat(addressesOf(mail, "To")).containsExactly("ana@example.com", "luis@example.com");
        assertThat(addressesOf(mail, "ReplyTo")).containsExactly("soporte@example.com");
        assertThat(mailSubject(mail)).isEqualTo("Factura F-0001");
        assertThat(trimEnd(mailText(mail))).isEqualTo("Hola Ana, total 10,00 €.");
        assertThat(trimEnd(mailHtml(mail))).isEqualTo("<p>Hola Ana, total 10,00 €.</p><p></p>");
        assertThat(mailText(mail)).doesNotContain("X");
        assertThat(mailHtml(mail)).doesNotContain("X");
        assertThat(messageIdLocalPart(mail)).isEqualTo(m1);

        // 4. getMessage: sent, con sus instantes ordenados.
        Response sent = messageOf(m1);
        assertStatus(sent, 200);
        String status = jsonPath(sent, "$.status");
        String sendingSince = jsonPath(sent, "$.sendingSince");
        String sentAt = jsonPath(sent, "$.sentAt");
        Object failedAt = jsonPath(sent, "$.failedAt");
        assertThat(status).isEqualTo("sent");
        assertThat(Instant.parse(requestedAt)).isBeforeOrEqualTo(Instant.parse(sendingSince));
        assertThat(Instant.parse(sendingSince)).isBeforeOrEqualTo(Instant.parse(sentAt));
        assertThat(failedAt).isNull();

        // 5. Un unico EmailSent con el data completo.
        Map<String, Object> event = awaitEvent("EmailSent", m1);
        assertJson(dataOf(event, "EmailSent"),
                emailSentData(m1, "billing", "inv-2026-0001", "invoice-issued", 1, sentAt));
        holdsFor(java.time.Duration.ofSeconds(5), () -> eventsFor("EmailSent", m1).size() == 1);
    }
}
