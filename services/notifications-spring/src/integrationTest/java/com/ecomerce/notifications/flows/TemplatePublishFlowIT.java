package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TemplatePublishFlowIT extends NotificationsFlowBase {

    private static final String VERSIONS = TEMPLATES_PATH + "/invoice-issued/versions";

    private String templateId;

    @BeforeAll
    void prepare() {
        resetState();
        // Given: editor creo invoice-issued (sin versiones).
        Response created = post(TEMPLATES_PATH, "{\"code\": \"invoice-issued\", \"name\": \"Factura emitida\"}",
                tokenFor(EDITOR));
        assertStatus(created, 200);
        templateId = jsonPath(created, "$.id");
    }

    private void assertVersionCount(int expected) {
        Response list = get(VERSIONS, tokenFor(EDITOR));
        assertStatus(list, 200);
        assertThat(totalElements(list)).isEqualTo(expected);
    }

    @Test
    @Order(1)
    @DisplayName("FL-TPL-010: publicar versiones, consultarlas y rechazos de contenido")
    void publishesVersions() {
        // When (1)
        Response first = publish("invoice-issued", TPL_V1);
        assertStatus(first, 200);
        String v1 = jsonPath(first, "$.id");
        String v1PublishedAt = jsonPath(first, "$.publishedAt");
        assertIsUuid(v1);
        assertIsInstant(v1PublishedAt);
        assertBody(first, versionJson(TPL_V1, v1, 1, "active", v1PublishedAt, templateId));
        Response template = get(TEMPLATES_PATH + "/invoice-issued", tokenFor(EDITOR));
        Boolean hasActive = jsonPath(template, "$.hasActiveVersion");
        assertThat(hasActive).isTrue();

        // When (2): segunda version con otro asunto.
        String content2 = withField(TPL_V1, "subject", "Tu factura {{invoiceNumber}}");
        Response second = publish("invoice-issued", content2);
        assertStatus(second, 200);
        String v2 = jsonPath(second, "$.id");
        String v2PublishedAt = jsonPath(second, "$.publishedAt");
        assertBody(second, versionJson(content2, v2, 2, "active", v2PublishedAt, templateId));

        Response v1Read = get(VERSIONS + "/1", tokenFor(EDITOR));
        assertStatus(v1Read, 200);
        assertBody(v1Read, versionJson(TPL_V1, v1, 1, "archived", v1PublishedAt, templateId));

        Response list = get(VERSIONS, tokenFor(EDITOR));
        assertStatus(list, 200);
        List<Object> descending = List.of(
                versionSummary(content2, v2, 2, "active", v2PublishedAt, templateId),
                versionSummary(TPL_V1, v1, 1, "archived", v1PublishedAt, templateId));
        assertBody(list, pageJson(descending, 0, 20, 2, 1));
        Response ascending = get(VERSIONS + "?sort=versionNumber,asc", tokenFor(EDITOR));
        assertStatus(ascending, 200);
        assertThat(idsOf(ascending)).containsExactly(v1, v2);
        assertStatus(get(VERSIONS + "?sort=status", tokenFor(EDITOR)), 400);
        assertStatus(get(VERSIONS + "?sort=subject", tokenFor(EDITOR)), 400);

        // When (3): marcador sin declarar.
        String undeclared = withField(TPL_V1, "subject", "Factura {{invoiceNumber}} {{ dueDate }}");
        assertError(publish("invoice-issued", undeclared), 422, "UNDECLARED_TEMPLATE_VARIABLES");
        assertVersionCount(2);

        // When (4): triple llave.
        String malformed = withField(TPL_V1, "htmlBody", "<p>Hola {{{customerName}}}</p>");
        assertError(publish("invoice-issued", malformed), 422, "MALFORMED_TEMPLATE_MARKER");
        assertVersionCount(2);

        // Casos borde: primero los rechazos (no crean versiones), al final los aceptados.
        rejectsBadContent();
        acceptsEdgeContent();
    }

    private void rejectsBadContent() {
        // Los rechazos no crean versiones: el flujo sigue con 2.
        Map<String, Object> duplicated = mapOf(TPL_V1);
        List<Object> twice = new ArrayList<>();
        twice.add(variable("amount", true));
        twice.add(variable("amount", true));
        duplicated.put("variables", twice);
        duplicated.put("subject", "Total {{amount}}");
        duplicated.put("htmlBody", "<p>{{amount}}</p>");
        duplicated.put("textBody", "{{amount}}");
        String duplicatedBody = toJson(duplicated);
        assertError(publish("invoice-issued", duplicatedBody), 422, "DUPLICATE_TEMPLATE_VARIABLES");
        // La guarda 2 (existe la plantilla) precede a la 3.
        assertError(publish("no-existe", duplicatedBody), 404, "TEMPLATE_NOT_FOUND");
        // La 3 precede a la 5 (marcador sin declarar).
        duplicated.put("subject", "Total {{amount}} {{ dueDate }}");
        assertError(publish("invoice-issued", toJson(duplicated)), 422, "DUPLICATE_TEMPLATE_VARIABLES");

        // Tabulador o salto de linea dentro de las llaves.
        assertError(publish("invoice-issued", withField(TPL_V1, "subject", "Factura {{\tinvoiceNumber}}")), 422,
                "MALFORMED_TEMPLATE_MARKER");
        assertError(publish("invoice-issued", withField(TPL_V1, "subject", "Factura {{\ninvoiceNumber}}")), 422,
                "MALFORMED_TEMPLATE_MARKER");
        // Marcadores mal formados en el texto.
        for (String text : List.of("{{ }}", "{{1x}}", "{{customerName")) {
            assertError(publish("invoice-issued", withField(TPL_V1, "textBody", text)), 422,
                    "MALFORMED_TEMPLATE_MARKER");
        }
        // La 4 precede a la 5.
        assertError(publish("invoice-issued", withField(TPL_V1, "htmlBody", "<p>{{{dueDate}}}</p>")), 422,
                "MALFORMED_TEMPLATE_MARKER");

        // Cotas de entrada.
        Map<String, Object> noText = mapOf(TPL_V1);
        noText.remove("textBody");
        assertError(publish("invoice-issued", toJson(noText)), 400, "VALIDATION_ERROR");
        assertError(publish("invoice-issued", withField(TPL_V1, "subject", "x".repeat(301))), 400, "VALIDATION_ERROR");
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(variable("var" + i, false));
        }
        assertError(publish("invoice-issued", withField(TPL_V1, "variables", many)), 400, "VALIDATION_ERROR");

        // Lecturas inexistentes.
        assertError(get(VERSIONS + "/9", tokenFor(EDITOR)), 404, "TEMPLATE_VERSION_NOT_FOUND");
        assertError(get(TEMPLATES_PATH + "/no-existe/versions", tokenFor(EDITOR)), 404, "TEMPLATE_NOT_FOUND");

        assertVersionCount(2);
    }

    private void acceptsEdgeContent() {
        // Espacios U+0020 dentro de las llaves: marcador bien formado.
        Response spaced = publish("invoice-issued", withField(TPL_V1, "subject", "Factura {{ invoiceNumber }}"));
        assertStatus(spaced, 200);
        Integer spacedNumber = jsonPath(spaced, "$.versionNumber");
        assertThat(spacedNumber).isEqualTo(3);
        // Llaves sueltas fuera de un marcador son texto literal.
        Response literal = publish("invoice-issued", withField(TPL_V1, "textBody", "Total {{amount}}} y fin }}"));
        assertStatus(literal, 200);
        Integer literalNumber = jsonPath(literal, "$.versionNumber");
        assertThat(literalNumber).isEqualTo(4);
    }

    private static Map<String, Object> variable(String name, boolean required) {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("name", name);
        variable.put("description", "Variable " + name);
        variable.put("required", required);
        return variable;
    }
}
