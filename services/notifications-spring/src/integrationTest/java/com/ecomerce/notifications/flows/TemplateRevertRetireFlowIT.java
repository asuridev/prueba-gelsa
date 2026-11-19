package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TemplateRevertRetireFlowIT extends NotificationsFlowBase {

    private static final String BASE = TEMPLATES_PATH + "/invoice-issued";

    private String templateId;
    private String v1;
    private String v2;
    private String content2;

    @BeforeAll
    void prepare() {
        resetState();
        // Given: P-TPL y editor publico la version 2 (la 1 queda archived).
        seedSender();
        Response created = post(TEMPLATES_PATH, "{\"code\": \"invoice-issued\", \"name\": \"Factura emitida\"}",
                tokenFor(EDITOR));
        assertStatus(created, 200);
        templateId = jsonPath(created, "$.id");
        Response first = publish("invoice-issued", TPL_V1);
        assertStatus(first, 200);
        v1 = jsonPath(first, "$.id");
        content2 = withField(TPL_V1, "subject", "Tu factura {{invoiceNumber}}");
        Response second = publish("invoice-issued", content2);
        assertStatus(second, 200);
        v2 = jsonPath(second, "$.id");
    }

    @Test
    @Order(1)
    @DisplayName("FL-TPL-020: revertir, retirar y volver a publicar")
    void revertRetireAndRepublish() {
        // When (1): activar la version 1.
        Response activated = post(BASE + "/versions/1/activate", "{}", tokenFor(EDITOR));
        assertStatus(activated, 200);
        String publishedAt = jsonPath(activated, "$.publishedAt");
        assertIsInstant(publishedAt);
        String expectedV1 = versionJson(TPL_V1, v1, 1, "active", publishedAt, templateId);
        assertBody(activated, expectedV1);
        Response v2Read = get(BASE + "/versions/2", tokenFor(EDITOR));
        String v2Status = jsonPath(v2Read, "$.status");
        assertThat(v2Status).isEqualTo("archived");

        // When (2): repetir es no-op.
        Response again = post(BASE + "/versions/1/activate", "{}", tokenFor(EDITOR));
        assertStatus(again, 200);
        assertBody(again, expectedV1);
        Response v2Again = get(BASE + "/versions/2", tokenFor(EDITOR));
        String v2StatusAgain = jsonPath(v2Again, "$.status");
        assertThat(v2StatusAgain).isEqualTo("archived");

        // Casos borde de la activacion.
        assertError(post(BASE + "/versions/7/activate", "{}", tokenFor(EDITOR)), 404, "TEMPLATE_VERSION_NOT_FOUND");
        assertError(post(TEMPLATES_PATH + "/no-existe/versions/1/activate", "{}", tokenFor(EDITOR)), 404,
                "TEMPLATE_NOT_FOUND");
        assertError(post(TEMPLATES_PATH + "/no-existe/retire", "{}", tokenFor(EDITOR)), 404, "TEMPLATE_NOT_FOUND");

        // When (3): retirar.
        Response retired = post(BASE + "/retire", "{}", tokenFor(EDITOR));
        assertStatus(retired, 200);
        assertBody(retired, templateJson(templateId, "invoice-issued", "Factura emitida", null, false));
        Response list = get(BASE + "/versions", tokenFor(EDITOR));
        assertStatus(list, 200);
        List<String> statuses = fieldOf(list, "status");
        assertThat(statuses).containsExactly("archived", "archived");
        assertThat(totalElements(list)).isEqualTo(2);

        // When (4): retirar otra vez.
        assertError(post(BASE + "/retire", "{}", tokenFor(EDITOR)), 409, "TEMPLATE_NOT_PUBLISHED");

        // When (5): publicar contenido nuevo; el numero no se reutiliza.
        Response republished = publish("invoice-issued", withField(TPL_V1, "subject", "Factura nueva {{invoiceNumber}}"));
        assertStatus(republished, 200);
        Integer number = jsonPath(republished, "$.versionNumber");
        String status = jsonPath(republished, "$.status");
        assertThat(number).isEqualTo(3);
        assertThat(status).isEqualTo("active");
        Response template = get(BASE, tokenFor(EDITOR));
        Boolean hasActive = jsonPath(template, "$.hasActiveVersion");
        assertThat(hasActive).isTrue();
        assertThat(v2).isNotNull();
    }
}
