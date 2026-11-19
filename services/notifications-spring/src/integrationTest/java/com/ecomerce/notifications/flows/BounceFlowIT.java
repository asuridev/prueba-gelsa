package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class BounceFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pidio R1 -> m1 (202 por contrato).
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
    }

    @BeforeEach
    void awaitSent() {
        awaitPreconditions(() -> awaitStatus(m1, "sent"));
    }

    private Response bounce(String messageId, String recipient, String type, String diagnostic) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient", recipient);
        body.put("bounceType", type);
        if (diagnostic != null) {
            body.put("diagnostic", diagnostic);
        }
        return post(MESSAGES_PATH + "/" + messageId + "/bounces", toJson(body), serviceCredential("mail-provider"));
    }

    private Response suppressed(String address) {
        return get(SUPPRESSIONS_PATH + "/" + address, tokenFor(OPERATOR));
    }

    @Test
    @Order(1)
    @DisplayName("FL-BNC-001: un rebote permanente suprime la direccion")
    void permanentBounceSuppresses() {
        // When (1): rebote blando -> no cambia nada.
        Response soft = bounce(m1, "Luis@Example.com", "transient", "mailbox full");
        assertStatus(soft, 204);
        assertThat(soft.body()).isNullOrEmpty();
        assertError(suppressed("luis@example.com"), 404, "SUPPRESSED_ADDRESS_NOT_FOUND");

        // When (2): rebote permanente.
        Response hard = bounce(m1, "Luis@Example.com", "permanent", "550 user unknown");
        assertStatus(hard, 204);
        Response record = suppressed("luis@example.com");
        assertStatus(record, 200);
        String a1 = jsonPath(record, "$.id");
        String notes = jsonPath(record, "$.notes");
        String suppressedAt = jsonPath(record, "$.suppressedAt");
        assertIsUuid(a1);
        assertIsInstant(suppressedAt);
        assertThat(notes).contains(m1).contains("550 user unknown");
        assertBody(record, recordJson(a1, "luis@example.com", notes, suppressedAt));
        // El rebote no cambia el mensaje.
        assertThat(statusOf(m1)).isEqualTo("sent");

        // When (3): repetir es no-op.
        assertStatus(bounce(m1, "Luis@Example.com", "permanent", "550 user unknown"), 204);
        assertBody(suppressed("luis@example.com"), recordJson(a1, "luis@example.com", notes, suppressedAt));

        // When (4): operator libera y el proveedor vuelve a rebotar.
        Response released = post(SUPPRESSIONS_PATH + "/luis@example.com/release", "{}", tokenFor(OPERATOR));
        assertStatus(released, 200);
        assertStatus(bounce(m1, "Luis@Example.com", "permanent", "550 user unknown"), 204);
        Response reactivated = suppressed("luis@example.com");
        assertStatus(reactivated, 200);
        String sameId = jsonPath(reactivated, "$.id");
        String reason = jsonPath(reactivated, "$.reason");
        String status = jsonPath(reactivated, "$.status");
        Object releasedAt = jsonPath(reactivated, "$.releasedAt");
        String newSuppressedAt = jsonPath(reactivated, "$.suppressedAt");
        assertThat(sameId).isEqualTo(a1);
        assertThat(reason).isEqualTo("hard-bounce");
        assertThat(status).isEqualTo("active");
        assertThat(releasedAt).isNull();
        assertThat(Instant.parse(newSuppressedAt)).isAfter(Instant.parse(suppressedAt));

        // When (5): diagnostic de 1000 caracteres.
        String longDiagnostic = "550 " + "x".repeat(996);
        assertStatus(bounce(m1, "ana@example.com", "permanent", longDiagnostic), 204);
        Response ana = suppressed("ana@example.com");
        assertStatus(ana, 200);
        String anaStatus = jsonPath(ana, "$.status");
        String anaReason = jsonPath(ana, "$.reason");
        String anaNotes = jsonPath(ana, "$.notes");
        assertThat(anaStatus).isEqualTo("active");
        assertThat(anaReason).isEqualTo("hard-bounce");
        assertThat(anaNotes).contains(m1).contains("550 ");
        assertThat(anaNotes.length()).isLessThanOrEqualTo(1000);

        // Casos borde.
        assertError(bounce(UUID.randomUUID().toString(), "ana@example.com", "permanent", "x"), 404,
                "MESSAGE_NOT_FOUND");
        assertError(bounce(m1, "otra@example.com", "permanent", "x"), 422, "RECIPIENT_NOT_IN_MESSAGE");
        assertError(post(MESSAGES_PATH + "/" + m1 + "/bounces",
                "{\"recipient\": \"ana@example.com\", \"bounceType\": \"permanent\"}", serviceCredential("billing")),
                403, "ACCESS_DENIED");
        assertError(post(MESSAGES_PATH + "/" + m1 + "/bounces",
                "{\"recipient\": \"ana@example.com\", \"bounceType\": \"permanent\"}"), 401, "UNAUTHENTICATED");
        assertError(bounce(m1, "ana@example.com", "soft", "x"), 400, "VALIDATION_ERROR");
    }

    private String recordJson(String id, String address, String notes, String suppressedAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("address", address);
        map.put("reason", "hard-bounce");
        map.put("notes", notes);
        map.put("status", "active");
        map.put("suppressedAt", suppressedAt);
        map.put("releasedAt", null);
        return toJson(map);
    }
}
