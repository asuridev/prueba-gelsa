package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class PersonalDataPurgeFlowIT extends NotificationsFlowBase {

    private static final String PURGE = ROUTE_BASE + "/maintenance/purge-personal-data";

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
    }

    @BeforeEach
    void awaitSent() {
        // Given: tras un ciclo m1 esta sent y hay un correo en el buzon.
        awaitPreconditions(() -> {
            awaitStatus(m1, "sent");
            awaitMailTo("ana@example.com", 1);
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PRG-001: purga manual y repeticion tras la purga")
    void purgesAndRepeats() {
        Response before = messageOf(m1);

        // When (1): admin purga.
        Response purge = post(PURGE, "{}", tokenFor(ADMIN));
        assertStatus(purge, 200);
        assertBody(purge, "{\"purgedCount\": 1}");

        // 2. m1 sin datos personales, el resto intacto.
        Response after = messageOf(m1);
        assertStatus(after, 200);
        Object recipients = jsonPath(after, "$.recipients");
        Object subject = jsonPath(after, "$.renderedSubject");
        String purgedAt = jsonPath(after, "$.personalDataPurgedAt");
        assertThat(toJson(recipients)).isEqualTo("[]");
        assertThat(subject).isNull();
        assertIsInstant(purgedAt);
        assertSameExcept(before, after, "recipients", "renderedSubject", "personalDataPurgedAt");

        // 3. Ya no casa por destinatario.
        Response byRecipient = get(MESSAGES_PATH + "?recipient=ana@example.com", tokenFor(ADMIN));
        assertThat(idsOf(byRecipient)).doesNotContain(m1);

        // When (2): repetir la purga.
        Response again = post(PURGE, "{}", tokenFor(ADMIN));
        assertStatus(again, 200);
        assertBody(again, "{\"purgedCount\": 0}");

        // When (3): billing repite R1.
        Response repeat = requestAs("billing", r1("inv-2026-0001"));
        assertStatus(repeat, 202);
        String repeatedId = jsonPath(repeat, "$.id");
        String repeatedPurgedAt = jsonPath(repeat, "$.personalDataPurgedAt");
        assertThat(repeatedId).isEqualTo(m1);
        assertThat(repeatedPurgedAt).isEqualTo(purgedAt);
        // No sale un segundo correo.
        holdsFor(Duration.ofSeconds(75), () -> mailCount("ana@example.com") == 1);

        // 6. Misma clave con otra plantilla: contenido distinto.
        Map<String, Object> other = mapOf(r1("inv-2026-0001"));
        other.put("templateCode", "other-tpl");
        assertError(requestAs("billing", toJson(other)), 409, "IDEMPOTENCY_KEY_REUSED");

        // 7. Un rebote de ana sobre m1 purgado: ya no esta entre sus destinatarios.
        Map<String, Object> bounce = new LinkedHashMap<>();
        bounce.put("recipient", "ana@example.com");
        bounce.put("bounceType", "permanent");
        assertError(post(MESSAGES_PATH + "/" + m1 + "/bounces", toJson(bounce), serviceCredential("mail-provider")),
                422, "RECIPIENT_NOT_IN_MESSAGE");

        // Casos borde.
        assertError(post(PURGE, "{}", tokenFor(OPERATOR)), 403, "ACCESS_DENIED");
        assertError(post(PURGE, "{}"), 401, "UNAUTHENTICATED");
    }
}
