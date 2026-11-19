package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class DispatchRenderFlowIT extends NotificationsFlowBase {

    private String m1;
    private String escapeId;
    private String crlfId;
    private String crId;

    private List<Map<String, String>> variables(String invoiceNumber, String customerName, String amount, String note) {
        return List.of(var("invoiceNumber", invoiceNumber), var("customerName", customerName), var("amount", amount),
                var("note", note));
    }

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given/When: la peticion principal y, cada una con su clave y su destinatario, los casos borde.
        m1 = requestAccepted("billing", requestJson("render-0001", "invoice-issued", List.of("ana@example.com"),
                variables("F-1\nX", "Ana <b>&</b>", "5 €", "Gracias")));
        escapeId = requestAccepted("billing", requestJson("render-0002", "invoice-issued",
                List.of("edge1@example.com"), variables("F-0002", "{{note}}", "5 €", "Gracias")));
        crlfId = requestAccepted("billing", requestJson("render-0003", "invoice-issued",
                List.of("edge2@example.com"), variables("F-1\r\nX", "Ana", "5 €", "Gracias")));
        crId = requestAccepted("billing", requestJson("render-0004", "invoice-issued",
                List.of("edge3@example.com"), variables("F-1\rX", "Ana", "5 €", "Gracias")));
    }

    @BeforeEach
    void awaitDispatch() {
        awaitPreconditions(() -> {
            for (String id : List.of(m1, escapeId, crlfId, crId)) {
                awaitStatus(id, "sent");
            }
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-DSP-030: render de los cuerpos, escapado y cabeceras")
    void rendersBodiesEscapingAndHeaders() {
        // 1. El asunto renderizado, en el mensaje y en el correo.
        Response message = messageOf(m1);
        String renderedSubject = jsonPath(message, "$.renderedSubject");
        assertThat(renderedSubject).isEqualTo("Factura F-1 X");
        Map<String, Object> mail = awaitMailOfMessage("ana@example.com", m1);
        assertThat(mailSubject(mail)).isEqualTo("Factura F-1 X");

        // 2. HTML escapado solo en & < > " '; el resto literal.
        assertThat(trimEnd(mailHtml(mail)))
                .isEqualTo("<p>Hola Ana &lt;b&gt;&amp;&lt;/b&gt;, total 5 €.</p><p>Gracias</p>");
        // 3. Texto plano sin escapar.
        assertThat(trimEnd(mailText(mail))).isEqualTo("Hola Ana <b>&</b>, total 5 €. Gracias");
        // 4. Cabeceras.
        assertThat(addressesOf(mail, "To")).containsExactly("ana@example.com");
        assertThat(addressesOf(mail, "ReplyTo")).containsExactly("soporte@example.com");
        assertThat(messageIdLocalPart(mail)).isEqualTo(m1);

        // Borde: una sola pasada, el valor insertado no se reinterpreta.
        Map<String, Object> escaped = awaitMailOfMessage("edge1@example.com", escapeId);
        assertThat(mailText(escaped)).startsWith("Hola {{note}}, total");

        // Borde: \r\n y \r sueltos cuentan cada uno como un solo salto.
        Response crlf = messageOf(crlfId);
        String crlfSubject = jsonPath(crlf, "$.renderedSubject");
        assertThat(crlfSubject).isEqualTo("Factura F-1 X");
        Response cr = messageOf(crId);
        String crSubject = jsonPath(cr, "$.renderedSubject");
        assertThat(crSubject).isEqualTo("Factura F-1 X");
    }
}
