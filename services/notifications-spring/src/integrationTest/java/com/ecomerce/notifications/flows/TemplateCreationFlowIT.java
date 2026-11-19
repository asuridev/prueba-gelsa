package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TemplateCreationFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-TPL-001: alta de plantilla, lectura y colision de code")
    void createsTemplate() {
        // When (1)
        Response created = post(TEMPLATES_PATH,
                "{\"code\": \"invoice-issued\", \"name\": \"Factura emitida\", \"description\": \"Aviso de factura\"}",
                tokenFor(EDITOR));
        assertStatus(created, 200);
        String t1 = jsonPath(created, "$.id");
        assertIsUuid(t1);
        String expected = templateJson(t1, "invoice-issued", "Factura emitida", "Aviso de factura", false);
        assertBody(created, expected);
        Response read = get(TEMPLATES_PATH + "/invoice-issued", tokenFor(EDITOR));
        assertStatus(read, 200);
        assertBody(read, expected);

        // When (2): misma code, otro name.
        Response duplicate = post(TEMPLATES_PATH, "{\"code\": \"invoice-issued\", \"name\": \"Otro nombre\"}",
                tokenFor(EDITOR));
        assertError(duplicate, 409, "TEMPLATE_ALREADY_EXISTS");
        Response after = get(TEMPLATES_PATH + "/invoice-issued", tokenFor(EDITOR));
        String name = jsonPath(after, "$.name");
        assertThat(name).isEqualTo("Factura emitida");

        // Casos borde
        assertError(get(TEMPLATES_PATH + "/no-existe", tokenFor(EDITOR)), 404, "TEMPLATE_NOT_FOUND");
        assertError(post(TEMPLATES_PATH, "{\"code\": \"Invoice\", \"name\": \"X\"}", tokenFor(EDITOR)), 400,
                "VALIDATION_ERROR");
        assertError(post(TEMPLATES_PATH, "{\"code\": \"ab\", \"name\": \"X\"}", tokenFor(EDITOR)), 400,
                "VALIDATION_ERROR");
        assertError(post(TEMPLATES_PATH, "{\"code\": \"sin-name\"}", tokenFor(EDITOR)), 400, "VALIDATION_ERROR");
    }
}
