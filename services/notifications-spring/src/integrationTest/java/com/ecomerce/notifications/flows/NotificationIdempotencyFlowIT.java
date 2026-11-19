package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationIdempotencyFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pidio R1 -> m1 (202 por contrato).
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
    }

    @BeforeEach
    void awaitSentFirstMessage() {
        // Precondicion que depende del despacho: m1 sent y un correo en el buzon.
        awaitPreconditions(() -> {
            awaitStatus(m1, "sent");
            awaitMailTo("ana@example.com", 1);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-002: repetir la misma peticion no manda un segundo correo")
    void repeatingDoesNotSendTwice() {
        // When (1): misma peticion con recipients y variables en otro orden y luis en mayusculas.
        List<Map<String, String>> reversed = new ArrayList<>(r1Variables());
        java.util.Collections.reverse(reversed);
        String repeated = requestJson("inv-2026-0001", "invoice-issued",
                List.of("LUIS@example.com", "ana@example.com"), reversed);
        Response repeat = requestAs("billing", repeated);

        // 1. 202 con el cuerpo de m1 tal como esta ahora.
        assertStatus(repeat, 202);
        assertJson(repeat.body(), messageOf(m1).body());
        String repeatedId = jsonPath(repeat, "$.id");
        String status = jsonPath(repeat, "$.status");
        assertThat(repeatedId).isEqualTo(m1);
        assertThat(status).isEqualTo("sent");

        // When (2): misma clave con otro importe.
        List<Map<String, String>> changed = List.of(var("invoiceNumber", "F-0001"), var("customerName", "Ana"),
                var("amount", "11,00 €"));
        Response reused = requestAs("billing", requestJson("inv-2026-0001", "invoice-issued",
                List.of("Ana@Example.com", "luis@example.com"), changed));
        // 3. 409 IDEMPOTENCY_KEY_REUSED.
        assertError(reused, 409, "IDEMPOTENCY_KEY_REUSED");

        // 2. Tras otro ciclo del despacho: un solo correo y un solo mensaje.
        holdsFor(Duration.ofSeconds(75), () -> mailCount("ana@example.com") == 1);
        Response all = get(MESSAGES_PATH, tokenFor(ADMIN));
        assertThat(totalElements(all)).isEqualTo(1);

        // When (3): otro llamante con la misma clave tiene su propio espacio.
        Response other = requestAs("shipping", r1("inv-2026-0001"));
        assertStatus(other, 202);
        String m2 = jsonPath(other, "$.id");
        String requestedBy = jsonPath(other, "$.requestedBy");
        assertThat(m2).isNotEqualTo(m1);
        assertThat(requestedBy).isEqualTo("shipping");
        awaitStatus(m2, "sent");
        List<Map<String, Object>> mails = awaitMails("ana@example.com", 2);
        assertThat(mailOfMessage(mails, m2)).isPresent();
        assertThat(mailOfMessage(mails, m1)).isPresent();
    }
}
