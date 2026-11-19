package com.ecomerce.notifications.flows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SenderSetupFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        // Given: plantilla creada y publicada por editor, SIN P-SND.
        seedTemplate("invoice-issued", "Factura emitida");
        seedVersion("invoice-issued", TPL_V1);
    }

    @Test
    @Order(1)
    @DisplayName("FL-SND-001: sin remitente no se envia; configurarlo y reemplazarlo")
    void senderLifecycle() {
        // When (1)
        Response notConfigured = get(SENDER_PATH, tokenFor(ADMIN));
        assertError(notConfigured, 404, "SENDER_SETTINGS_NOT_CONFIGURED");

        // When (2)
        Response request = requestAs("billing", r1("inv-2026-0001"));
        assertError(request, 404, "SENDER_SETTINGS_NOT_CONFIGURED");
        assertError(findByKey("billing", "inv-2026-0001"), 404, "MESSAGE_NOT_FOUND");
        // Tras un ciclo del despacho el buzon sigue vacio (assertNoMailTo espera mas que un ciclo).
        assertNoMailTo("ana@example.com");

        // When (3)
        Response configured = put(SENDER_PATH, P_SND, tokenFor(ADMIN));
        assertStatus(configured, 200);
        String s1 = jsonPath(configured, "$.id");
        assertIsUuid(s1);
        String expected = """
            {"id": "%s", "key": "default", "senderAddress": "avisos@notify.example.com",
             "senderName": "Acme Avisos", "replyToAddress": "soporte@example.com"}""".formatted(s1);
        assertBody(configured, expected);
        Response read = get(SENDER_PATH, tokenFor(ADMIN));
        assertStatus(read, 200);
        assertBody(read, expected);

        // When (4)
        Response replaced = put(SENDER_PATH, "{\"senderAddress\": \"Avisos2@Notify.Example.com\"}", tokenFor(ADMIN));
        assertStatus(replaced, 200);
        assertBody(replaced, """
            {"id": "%s", "key": "default", "senderAddress": "avisos2@notify.example.com",
             "senderName": null, "replyToAddress": null}""".formatted(s1));

        // Casos borde
        assertError(put(SENDER_PATH, "{\"senderName\": \"Sin direccion\"}", tokenFor(ADMIN)), 400, "VALIDATION_ERROR");
        assertError(put(SENDER_PATH, "{\"senderAddress\": \"no-es-correo\"}", tokenFor(ADMIN)), 400, "VALIDATION_ERROR");
    }
}
