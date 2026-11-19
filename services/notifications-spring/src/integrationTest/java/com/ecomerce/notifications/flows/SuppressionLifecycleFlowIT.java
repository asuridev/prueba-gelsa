package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SuppressionLifecycleFlowIT extends NotificationsFlowBase {

    private static final String PEPE = SUPPRESSIONS_PATH + "/pepe@example.com";

    @BeforeAll
    void prepare() {
        resetState();
        seedSender();
    }

    private String recordJson(String id, String reason, String notes, String status, String suppressedAt,
            String releasedAt) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("address", "pepe@example.com");
        map.put("reason", reason);
        map.put("notes", notes);
        map.put("status", status);
        map.put("suppressedAt", suppressedAt);
        map.put("releasedAt", releasedAt);
        return toJson(map);
    }

    @Test
    @Order(1)
    @DisplayName("FL-SUP-001: suprimir, consultar, liberar y volver a suprimir")
    void suppressReleaseAndSuppressAgain() {
        // When (1): suprimir.
        String notes = "pidió no recibir";
        Response created = put(SUPPRESSIONS_PATH + "/Pepe@Example.com",
                toJson(Map.of("reason", "complaint", "notes", notes)), tokenFor(OPERATOR));
        assertStatus(created, 200);
        String a1 = jsonPath(created, "$.id");
        String suppressedAt = jsonPath(created, "$.suppressedAt");
        assertIsUuid(a1);
        assertIsInstant(suppressedAt);
        String active = recordJson(a1, "complaint", notes, "active", suppressedAt, null);
        assertBody(created, active);

        // 2. Repetirlo con otro motivo es no-op.
        Response repeat = put(PEPE, "{\"reason\": \"manual\"}", tokenFor(OPERATOR));
        assertStatus(repeat, 200);
        assertBody(repeat, active);

        // 3. El listado lo contiene.
        Response list = get(SUPPRESSIONS_PATH, tokenFor(OPERATOR));
        assertStatus(list, 200);
        assertThat(idsOf(list)).containsExactly(a1);
        assertThat(totalElements(list)).isEqualTo(1);

        // When (2): liberar dos veces.
        Response first = post(PEPE + "/release", "{}", tokenFor(OPERATOR));
        assertStatus(first, 200);
        String releasedAt = jsonPath(first, "$.releasedAt");
        String firstStatus = jsonPath(first, "$.status");
        assertIsInstant(releasedAt);
        assertThat(firstStatus).isEqualTo("released");
        Response second = post(PEPE + "/release", "{}", tokenFor(OPERATOR));
        assertStatus(second, 200);
        String secondStatus = jsonPath(second, "$.status");
        String secondReleasedAt = jsonPath(second, "$.releasedAt");
        assertThat(secondStatus).isEqualTo("released");
        assertThat(secondReleasedAt).isEqualTo(releasedAt);

        // 5. El registro queda released y el listado vacio.
        Response read = get(PEPE, tokenFor(OPERATOR));
        assertStatus(read, 200);
        assertBody(read, recordJson(a1, "complaint", notes, "released", suppressedAt, releasedAt));
        Response empty = get(SUPPRESSIONS_PATH, tokenFor(OPERATOR));
        assertBody(empty, pageJson(List.of(), 0, 20, 0, 0));

        // When (3): suprimir de nuevo, sin notes.
        Response again = put(PEPE, "{\"reason\": \"manual\"}", tokenFor(OPERATOR));
        assertStatus(again, 200);
        String sameId = jsonPath(again, "$.id");
        String reason = jsonPath(again, "$.reason");
        String status = jsonPath(again, "$.status");
        Object newNotes = jsonPath(again, "$.notes");
        Object newReleasedAt = jsonPath(again, "$.releasedAt");
        String newSuppressedAt = jsonPath(again, "$.suppressedAt");
        assertThat(sameId).isEqualTo(a1);
        assertThat(reason).isEqualTo("manual");
        assertThat(status).isEqualTo("active");
        assertThat(newNotes).isNull();
        assertThat(newReleasedAt).isNull();
        assertThat(Instant.parse(newSuppressedAt)).isAfter(Instant.parse(suppressedAt));

        // Casos borde.
        assertError(post(SUPPRESSIONS_PATH + "/nunca@example.com/release", "{}", tokenFor(OPERATOR)), 404,
                "SUPPRESSED_ADDRESS_NOT_FOUND");
        assertError(get(SUPPRESSIONS_PATH + "/nunca@example.com", tokenFor(OPERATOR)), 404,
                "SUPPRESSED_ADDRESS_NOT_FOUND");
        assertError(put(SUPPRESSIONS_PATH + "/otro@example.com", "{\"reason\": \"bounced\"}", tokenFor(OPERATOR)), 400,
                "VALIDATION_ERROR");
        assertError(put(SUPPRESSIONS_PATH + "/no-es-correo", "{\"reason\": \"manual\"}", tokenFor(OPERATOR)), 400,
                "VALIDATION_ERROR");
    }
}
