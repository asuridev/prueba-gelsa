package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * FL-EVT-003: con el almacén caído la suscripción agota sus reintentos y el mensaje acaba en la
 * deadLetter; restablecido el almacén y reinyectado dos veces, se envía una sola vez.
 *
 * <p>Reinyectar desde la deadLetter es volver a entregar el MISMO mensaje (mismo
 * {@code metadata.eventId}) en el canal de la suscripción: por eso se escribe con
 * {@code deliverNotificationRequested} y el mismo {@code messageId} de la entrega original.
 */
class EventRetryReinjectFlowIT extends NotificationsFlowBase {

    private static final String SUBSCRIPTION = "NotificationRequested";

    /**
     * Techo para que el mensaje llegue al descarte con la base parada: 3 intentos con backoff
     * exponencial (1 s, 2 s) más lo que tarde cada intento en desistir de conseguir una conexión.
     */
    private static final Duration DEAD_LETTER_WAIT = Duration.ofSeconds(240);

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    private int totalMessages() {
        return totalElements(get(MESSAGES_PATH, tokenFor(ADMIN)));
    }

    /** Mensajes de la deadLetter de la suscripción que llevan ese eventId. */
    private long deadLettersOf(String eventId) {
        String raw = deadLetterMessages(SUBSCRIPTION, 20);
        if (raw.isBlank()) {
            return 0;
        }
        // Cada elemento es un mensaje del broker (propiedades + sobre): el eventId viaja en el
        // sobre y, como identidad del mensaje, en sus propiedades. Se cuenta por mensaje.
        List<Object> messages = JsonPath.read(raw, "$[*]");
        long count = 0;
        for (Object message : messages) {
            String json = toJson(message);
            if (json.contains(eventId)) {
                count++;
            }
        }
        return count;
    }

    @Test
    @Order(1)
    @DisplayName("FL-EVT-003: un fallo transitorio se reintenta y, reinyectado, envia una sola vez")
    void transientFailureRetriedAndReinjectedOnce() {
        String e1 = UUID.randomUUID().toString();
        String body = r1("inv-2026-0001");

        // Given: almacen indisponible durante los reintentos de la suscripcion.
        stopDatabase();
        try {
            // When: llega NotificationRequested de billing con R1; se agotan los 3 intentos.
            deliverNotificationRequested(e1, "billing", body);

            // 1. Agotados los reintentos, el mensaje esta en la deadLetter.
            await(DEAD_LETTER_WAIT, () -> deadLettersOf(e1) == 1);
        } finally {
            // Siempre: una base caida envenena los flujos siguientes.
            startDatabase();
        }

        // 1. Restablecido el almacen y ANTES de reinyectar: la peticion no se acepto.
        //    Se espera solo a que el servicio vuelva a responder sin 5xx (el pool reconecta solo);
        //    lo que se afirma es el 404 del contrato.
        await(Duration.ofSeconds(60), () -> findByKey("billing", "inv-2026-0001").status() < 500);
        assertError(findByKey("billing", "inv-2026-0001"), 404, "MESSAGE_NOT_FOUND");
        assertThat(totalMessages()).isZero();

        // When: se reinyecta desde la deadLetter dos veces (el MISMO mensaje, mismo eventId).
        deliverNotificationRequested(e1, "billing", body);
        deliverNotificationRequested(e1, "billing", body);

        // 2. Existe exactamente un mensaje de inv-2026-0001...
        await(Duration.ofSeconds(60), () -> findByKey("billing", "inv-2026-0001").status() == 200);
        Response found = findByKey("billing", "inv-2026-0001");
        String m1 = jsonPath(found, "$.id");
        String requestedBy = jsonPath(found, "$.requestedBy");
        assertIsUuid(m1);
        assertThat(requestedBy).isEqualTo("billing");

        // ... y el buzon recibe UN correo.
        awaitStatus(m1, "sent");
        Map<String, Object> mail = awaitMailOfMessage("ana@example.com", m1);
        assertThat(messageIdLocalPart(mail)).isEqualTo(m1);
        holdsFor(Duration.ofSeconds(75), () -> totalMessages() == 1 && mailCount("ana@example.com") == 1);
        assertThat(mailsOfMessage("ana@example.com", m1)).isEqualTo(1);

        // La reinyeccion repetida se absorbe: no acaba otra vez en la deadLetter.
        assertThat(deadLettersOf(e1)).isEqualTo(1);
    }
}
