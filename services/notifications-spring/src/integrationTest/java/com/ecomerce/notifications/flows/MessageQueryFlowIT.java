package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class MessageQueryFlowIT extends NotificationsFlowBase {

    private String v1;
    private String k1;
    private String k2;
    private String k3;

    @BeforeAll
    void prepare() {
        resetState();
        v1 = seedP_TPL();
        // Given, en este orden: billing key-0001 a ana, billing key-0002 a luis, shipping key-0003 a zed@rejected.invalid.
        k1 = requestAccepted("billing", r1To("key-0001", "ana@example.com"));
        k2 = requestAccepted("billing", r1To("key-0002", "luis@example.com"));
        k3 = requestAccepted("shipping", r1To("key-0003", rejectedAddress("zed")));
    }

    @BeforeEach
    void awaitDispatch() {
        // Tras un ciclo: key-0001 y key-0002 sent, key-0003 failed (delivery-rejected).
        awaitPreconditions(() -> {
            awaitStatus(k1, "sent");
            awaitStatus(k2, "sent");
            awaitStatus(k3, "failed");
        });
    }

    private Response list(String query) {
        return get(MESSAGES_PATH + query, tokenFor(OPERATOR));
    }

    @Test
    @Order(1)
    @DisplayName("FL-MSG-001: leer y listar correos con filtros")
    void readsAndListsWithFilters() {
        // 1. La proyeccion completa de EmailMessage.
        Response one = get(MESSAGES_PATH + "/" + k1, tokenFor(OPERATOR));
        assertStatus(one, 200);
        String requestedAt1 = jsonPath(one, "$.requestedAt");
        String sendingSince = jsonPath(one, "$.sendingSince");
        String sentAt = jsonPath(one, "$.sentAt");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("id", k1);
        expected.put("requestedBy", "billing");
        expected.put("idempotencyKey", "key-0001");
        expected.put("recipients", List.of("ana@example.com"));
        expected.put("renderedSubject", "Factura F-0001");
        expected.put("sender", mapOf("""
            {"address": "avisos@notify.example.com", "name": "Acme Avisos", "replyToAddress": "soporte@example.com"}"""));
        expected.put("templateCode", "invoice-issued");
        expected.put("templateVersionNumber", 1);
        expected.put("templateVersionId", v1);
        expected.put("status", "sent");
        expected.put("requestedAt", requestedAt1);
        expected.put("sendingSince", sendingSince);
        expected.put("sentAt", sentAt);
        expected.put("failedAt", null);
        expected.put("failureReason", null);
        expected.put("personalDataPurgedAt", null);
        assertBody(one, toJson(expected));

        // 2. Id inexistente.
        assertError(get(MESSAGES_PATH + "/" + UUID.randomUUID(), tokenFor(OPERATOR)), 404, "MESSAGE_NOT_FOUND");

        // 3. Sin filtros: requestedAt descendente.
        Response all = list("");
        assertStatus(all, 200);
        assertThat(idsOf(all)).containsExactly(k3, k2, k1);
        assertThat(totalElements(all)).isEqualTo(3);

        // 4. Filtro por destinatario, sin distinguir mayusculas.
        assertThat(idsOf(list("?recipient=ANA@example.com"))).containsExactly(k1);

        // 5. Filtros por llamante, estado y plantilla.
        assertThat(idsOf(list("?requestedBy=billing"))).containsExactly(k2, k1);
        assertThat(idsOf(list("?requestedBy=shipping"))).containsExactly(k3);
        assertThat(idsOf(list("?status=failed"))).containsExactly(k3);
        assertThat(idsOf(list("?templateCode=invoice-issued&status=sent"))).containsExactly(k2, k1);

        // 6. Ventana temporal: from inclusivo, to exclusivo.
        Response two = get(MESSAGES_PATH + "/" + k2, tokenFor(OPERATOR));
        String requestedAt2 = jsonPath(two, "$.requestedAt");
        assertThat(idsOf(list("?requestedFrom=" + requestedAt2))).containsExactly(k3, k2);
        assertThat(idsOf(list("?requestedTo=" + requestedAt2))).containsExactly(k1);

        // 7. Ventana invertida.
        assertError(list("?requestedFrom=2026-10-05T10:00:00Z&requestedTo=2026-10-05T09:00:00Z"), 422,
                "INVALID_TIME_WINDOW");

        // 8. Ningun elemento trae variableValues.
        assertThat(all.body()).doesNotContain("variableValues");
        assertThat(one.body()).doesNotContain("variableValues");
    }
}
