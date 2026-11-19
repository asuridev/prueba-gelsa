package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationRejectionsFlowIT extends NotificationsFlowBase {

    private static final List<String> ANA_LUIS = List.of("ana@example.com", "luis@example.com");

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // draft-tpl sin publicar; topic-only con su version 1; blocked@example.com suprimida.
        seedTemplate("draft-tpl", "Borrador");
        seedTemplate("topic-only", "Solo tema");
        seedVersion("topic-only", """
            {"subject": "{{topic}}", "htmlBody": "<p>Aviso</p>", "textBody": "Aviso",
             "variables": [{"name": "topic", "description": "Tema", "required": false}]}""");
        suppress("blocked@example.com", "{\"reason\": \"manual\"}");
    }

    private Response ask(String key, String template, List<String> recipients, List<Map<String, String>> variables) {
        return requestAs("billing", requestJson(key, template, recipients, variables));
    }

    private List<Map<String, String>> without(String name) {
        List<Map<String, String>> variables = new ArrayList<>();
        for (Map<String, String> variable : r1Variables()) {
            if (!name.equals(variable.get("name"))) {
                variables.add(variable);
            }
        }
        return variables;
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-010: rechazos de la peticion, su precedencia y que no salga correo")
    void rejectionsAndPrecedence() {
        List<String> blocked = List.of("ana@example.com", "Blocked@Example.com");
        List<String> rejectedKeys = new ArrayList<>();

        // 1. Plantilla inexistente.
        rejectedKeys.add("var-0001");
        assertError(ask("var-0001", "no-existe", ANA_LUIS, r1Variables()), 404, "TEMPLATE_NOT_FOUND");
        // 2. Sin version activa.
        rejectedKeys.add("var-0002");
        assertError(ask("var-0002", "draft-tpl", ANA_LUIS, r1Variables()), 409, "TEMPLATE_NOT_PUBLISHED");
        // 3. Falta la variable required amount.
        rejectedKeys.add("var-0003");
        assertError(ask("var-0003", "invoice-issued", ANA_LUIS, without("amount")), 422, "MISSING_TEMPLATE_VARIABLES");
        // 4. Destinatario suprimido.
        rejectedKeys.add("var-0004");
        assertError(ask("var-0004", "invoice-issued", blocked, r1Variables()), 422, "RECIPIENT_SUPPRESSED");
        // 5. Dos valores para amount.
        rejectedKeys.add("var-0005");
        List<Map<String, String>> duplicated = r1Variables();
        duplicated.add(var("amount", "12,00"));
        assertError(ask("var-0005", "invoice-issued", ANA_LUIS, duplicated), 422, "DUPLICATE_TEMPLATE_VARIABLES");
        // 6. Asunto renderizado de 301 caracteres.
        rejectedKeys.add("var-0006");
        assertError(ask("var-0006", "invoice-issued", ANA_LUIS, withInvoiceNumber("x".repeat(293))), 422,
                "RENDERED_SUBJECT_TOO_LONG");
        // 7. Asunto vacio: sin variable, y solo espacios.
        rejectedKeys.add("var-0007");
        assertError(ask("var-0007", "topic-only", ANA_LUIS, List.of()), 422, "EMPTY_RENDERED_SUBJECT");
        rejectedKeys.add("var-0008");
        assertError(ask("var-0008", "topic-only", ANA_LUIS, List.of(var("topic", "   "))), 422,
                "EMPTY_RENDERED_SUBJECT");

        // Precedencia (casos borde): cada una gana a las posteriores.
        rejectedKeys.add("var-0009");
        assertError(ask("var-0009", "no-existe", blocked, without("amount")), 404, "TEMPLATE_NOT_FOUND");
        rejectedKeys.add("var-0010");
        assertError(ask("var-0010", "draft-tpl", ANA_LUIS, duplicated), 422, "DUPLICATE_TEMPLATE_VARIABLES");
        rejectedKeys.add("var-0011");
        assertError(ask("var-0011", "invoice-issued", blocked, without("amount")), 422, "MISSING_TEMPLATE_VARIABLES");
        rejectedKeys.add("var-0012");
        assertError(ask("var-0012", "topic-only", blocked, List.of()), 422, "EMPTY_RENDERED_SUBJECT");
        rejectedKeys.add("var-0013");
        assertError(ask("var-0013", "invoice-issued", blocked, withInvoiceNumber("x".repeat(293))), 422,
                "RENDERED_SUBJECT_TOO_LONG");

        // Cotas de entrada: 400.
        assertError(ask("var-0014", "invoice-issued", List.of(), r1Variables()), 400, "VALIDATION_ERROR");
        List<String> twentyOne = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOne.add("many" + i + "@example.com");
        }
        assertError(ask("var-0015", "invoice-issued", twentyOne, r1Variables()), 400, "VALIDATION_ERROR");
        assertError(ask("abc", "invoice-issued", ANA_LUIS, r1Variables()), 400, "VALIDATION_ERROR");
        assertError(ask("var-0016", "invoice-issued", List.of("no-es-correo"), r1Variables()), 400,
                "VALIDATION_ERROR");

        // 8. Ninguna de las rechazadas existe y, tras un ciclo, el buzon sigue vacio.
        for (String key : rejectedKeys) {
            assertError(findByKey("billing", key), 404, "MESSAGE_NOT_FOUND");
        }
        assertNoMailTo("ana@example.com");
        assertNoMailTo("luis@example.com");

        // Aceptadas (casos borde): topic "Hola" y asunto de 300 caracteres justos.
        Response hola = ask("var-0020", "topic-only", List.of("ok1@example.com"), List.of(var("topic", "Hola")));
        assertStatus(hola, 202);
        String renderedSubject = jsonPath(hola, "$.renderedSubject");
        assertThat(renderedSubject).isEqualTo("Hola");
        assertStatus(ask("var-0021", "invoice-issued", List.of("ok2@example.com"), withInvoiceNumber("x".repeat(292))),
                202);

        // La repeticion precede a la supresion.
        Response first = requestAs("billing", r1("inv-2026-0001"));
        assertStatus(first, 202);
        String m1 = jsonPath(first, "$.id");
        suppress("ana@example.com", "{\"reason\": \"manual\"}");
        Response repeat = requestAs("billing", r1("inv-2026-0001"));
        assertStatus(repeat, 202);
        String repeatedId = jsonPath(repeat, "$.id");
        assertThat(repeatedId).isEqualTo(m1);
    }

    private List<Map<String, String>> withInvoiceNumber(String value) {
        List<Map<String, String>> variables = new ArrayList<>();
        for (Map<String, String> variable : r1Variables()) {
            variables.add("invoiceNumber".equals(variable.get("name")) ? var("invoiceNumber", value) : variable);
        }
        return variables;
    }
}
