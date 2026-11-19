package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Preparaciones y lecturas compartidas por las clases de flujo de notifications (P-SND, P-TPL,
 * la petición estandar R1, lectura de eventos y del buzon). No es fontaneria del arnes: solo
 * el vocabulario del diseño, para que cada clase de flujo se lea como su escenario.
 *
 * <p>Caja negra: solo HTTP, JSON, el buzon y el canal de eventos.
 */
abstract class NotificationsFlowBase extends AbstractFlowIT {

    protected static final String ADMIN = "notifications-admin";
    protected static final String EDITOR = "template-editor";
    protected static final String OPERATOR = "notifications-operator";
    protected static final String AUDITOR = "notifications-auditor";

    protected static final String SENDER_PATH = ROUTE_BASE + "/sender-settings";
    protected static final String TEMPLATES_PATH = ROUTE_BASE + "/templates";
    protected static final String NOTIFICATIONS_PATH = ROUTE_BASE + "/notifications";
    protected static final String MESSAGES_PATH = ROUTE_BASE + "/messages";
    protected static final String SUPPRESSIONS_PATH = ROUTE_BASE + "/suppressions";
    protected static final String EVENTS = "notificationEvents";

    /** Techo de espera de un ciclo del despacho (70 s de contrato, con margen). */
    protected static final Duration CYCLE = Duration.ofSeconds(90);

    /** P-SND. */
    protected static final String P_SND = """
            {"senderAddress": "Avisos@Notify.Example.com", "senderName": "Acme Avisos", "replyToAddress": "soporte@example.com"}""";

    /** Version 1 de P-TPL. */
    protected static final String TPL_V1 = """
            {
              "subject": "Factura {{invoiceNumber}}",
              "htmlBody": "<p>Hola {{ customerName }}, total {{amount}}.</p><p>{{note}}</p>",
              "textBody": "Hola {{customerName}}, total {{amount}}. {{note}}",
              "variables": [
                {"name": "invoiceNumber", "description": "Número de factura", "required": true},
                {"name": "customerName", "description": "Nombre del cliente", "required": true},
                {"name": "amount", "description": "Importe con moneda", "required": true},
                {"name": "note", "description": "Nota opcional", "required": false}
              ]
            }""";

    // ── Preparaciones ────────────────────────────────────────────────────────

    protected void seedSender() {
        Response response = put(SENDER_PATH, P_SND, tokenFor(ADMIN));
        assertThat(response.status()).as("P-SND: " + response.body()).isEqualTo(200);
    }

    protected void seedTemplate(String code, String name) {
        Response response = post(TEMPLATES_PATH, toJson(Map.of("code", code, "name", name)), tokenFor(EDITOR));
        assertThat(response.status()).as("alta de " + code + ": " + response.body()).isEqualTo(200);
    }

    protected Response publish(String code, String body) {
        return post(TEMPLATES_PATH + "/" + code + "/versions", body, tokenFor(EDITOR));
    }

    /** Publica y devuelve el id de la version. */
    protected String seedVersion(String code, String body) {
        Response response = publish(code, body);
        assertThat(response.status()).as("publicar en " + code + ": " + response.body()).isEqualTo(200);
        String versionId = jsonPath(response, "$.id");
        return versionId;
    }

    /** P-TPL: P-SND, plantilla invoice-issued y su version 1. Devuelve el id de la version 1. */
    protected String seedP_TPL() {
        seedSender();
        seedTemplate("invoice-issued", "Factura emitida");
        return seedVersion("invoice-issued", TPL_V1);
    }

    protected void suppress(String address, String json) {
        Response response = put(SUPPRESSIONS_PATH + "/" + address, json, tokenFor(OPERATOR));
        assertThat(response.status()).as("suprimir " + address + ": " + response.body()).isEqualTo(200);
    }

    // ── Peticiones de envio ──────────────────────────────────────────────────

    protected static Map<String, String> var(String name, String value) {
        Map<String, String> variable = new LinkedHashMap<>();
        variable.put("name", name);
        variable.put("value", value);
        return variable;
    }

    protected static List<Map<String, String>> r1Variables() {
        return new ArrayList<>(List.of(
                var("invoiceNumber", "F-0001"),
                var("customerName", "Ana"),
                var("amount", "10,00 €")));
    }

    protected String requestJson(String key, String templateCode, List<String> recipients,
            List<Map<String, String>> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", key);
        body.put("templateCode", templateCode);
        body.put("recipients", recipients);
        body.put("variables", variables);
        return toJson(body);
    }

    /** R1 con su clave. */
    protected String r1(String key) {
        return requestJson(key, "invoice-issued", List.of("Ana@Example.com", "luis@example.com"), r1Variables());
    }

    /** R1 con otros destinatarios. */
    protected String r1To(String key, String... recipients) {
        return requestJson(key, "invoice-issued", List.of(recipients), r1Variables());
    }

    protected Response requestAs(String client, String body) {
        return post(NOTIFICATIONS_PATH, body, serviceCredential(client));
    }

    /** Pide el envio y exige 202; devuelve el id del mensaje. */
    protected String requestAccepted(String client, String body) {
        Response response = requestAs(client, body);
        assertThat(response.status()).as("requestNotification: " + response.body()).isEqualTo(202);
        String id = jsonPath(response, "$.id");
        assertIsUuid(id);
        return id;
    }

    protected Response findByKey(String client, String key) {
        return get(NOTIFICATIONS_PATH + "/" + key, serviceCredential(client));
    }

    // ── Lectura de mensajes ──────────────────────────────────────────────────

    protected Response messageOf(String id) {
        return get(MESSAGES_PATH + "/" + id, tokenFor(ADMIN));
    }

    protected String statusOf(String id) {
        Response response = messageOf(id);
        if (response.status() != 200) {
            return "HTTP-" + response.status();
        }
        String status = jsonPath(response, "$.status");
        return status;
    }

    protected void awaitStatus(String id, String expected) {
        await(CYCLE, () -> expected.equals(statusOf(id)));
    }

    protected void assertError(Response response, int status, String code) {
        assertThat(response.status()).as("cuerpo: " + response.body()).isEqualTo(status);
        String actual = jsonPath(response, "$.code");
        assertThat(actual).isEqualTo(code);
    }

    protected void assertStatus(Response response, int status) {
        assertThat(response.status()).as("cuerpo: " + response.body()).isEqualTo(status);
    }

    protected Map<String, Object> bodyMap(Response response) {
        Map<String, Object> map = JsonPath.read(response.body(), "$");
        return new LinkedHashMap<>(map);
    }

    protected Map<String, Object> mapOf(String json) {
        Map<String, Object> map = JsonPath.read(json, "$");
        return new LinkedHashMap<>(map);
    }

    /** Dos cuerpos de mensaje iguales salvo en las claves indicadas. */
    protected void assertSameExcept(Response expected, Response actual, String... ignoredKeys) {
        Map<String, Object> a = bodyMap(expected);
        Map<String, Object> b = bodyMap(actual);
        for (String key : ignoredKeys) {
            a.remove(key);
            b.remove(key);
        }
        assertJson(toJson(b), toJson(a));
    }

    protected List<Map<String, Object>> itemsOf(Response page) {
        List<Map<String, Object>> items = JsonPath.read(page.body(), "$.items");
        return items;
    }

    protected List<String> idsOf(Response page) {
        List<String> ids = JsonPath.read(page.body(), "$.items[*].id");
        return ids;
    }

    protected List<String> fieldOf(Response page, String field) {
        List<String> values = JsonPath.read(page.body(), "$.items[*]." + field);
        return values;
    }

    protected int totalElements(Response page) {
        Integer total = JsonPath.read(page.body(), "$.totalElements");
        return total;
    }

    // ── Versiones de plantilla ───────────────────────────────────────────────

    protected String withField(String json, String key, Object value) {
        Map<String, Object> map = mapOf(json);
        map.put(key, value);
        return toJson(map);
    }

    protected String versionJson(String content, String id, int number, String status, String publishedAt,
            String templateId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("versionNumber", number);
        map.putAll(mapOf(content));
        map.put("status", status);
        map.put("publishedAt", publishedAt);
        map.put("emailTemplateId", templateId);
        return toJson(map);
    }

    protected Map<String, Object> versionSummary(String content, String id, int number, String status,
            String publishedAt, String templateId) {
        Map<String, Object> map = mapOf(versionJson(content, id, number, status, publishedAt, templateId));
        map.remove("htmlBody");
        map.remove("textBody");
        return map;
    }

    protected String templateJson(String id, String code, String name, String description, boolean hasActive) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("code", code);
        map.put("name", name);
        map.put("description", description);
        map.put("hasActiveVersion", hasActive);
        return toJson(map);
    }

    protected String pageJson(List<?> items, int page, int size, int totalElements, int totalPages) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", items);
        map.put("page", page);
        map.put("size", size);
        map.put("totalElements", totalElements);
        map.put("totalPages", totalPages);
        return toJson(map);
    }

    // ── Eventos publicados ───────────────────────────────────────────────────

    /** Sobres {metadata, data} del tipo dado publicados en notificationEvents. */
    protected List<Map<String, Object>> eventsOfType(String eventType) {
        String raw = publishedMessages(EVENTS, 100);
        List<Map<String, Object>> payloads = JsonPath.read(raw, "$[*].payload");
        List<Map<String, Object>> matching = new ArrayList<>();
        for (Map<String, Object> payload : payloads) {
            Object type = JsonPath.read(payload, "$.metadata.eventType");
            if (eventType.equals(type)) {
                matching.add(payload);
            }
        }
        return matching;
    }

    protected List<Map<String, Object>> eventsFor(String eventType, String messageId) {
        List<Map<String, Object>> matching = new ArrayList<>();
        for (Map<String, Object> payload : eventsOfType(eventType)) {
            Object id = JsonPath.read(payload, "$.data.messageId");
            if (messageId.equals(id)) {
                matching.add(payload);
            }
        }
        return matching;
    }

    protected Map<String, Object> awaitEvent(String eventType, String messageId) {
        await(Duration.ofSeconds(60), () -> !eventsFor(eventType, messageId).isEmpty());
        return eventsFor(eventType, messageId).get(0);
    }

    /** Que el sobre lleva metadata de forma correcta; devuelve su data como JSON. */
    protected String dataOf(Map<String, Object> envelope, String eventType) {
        String actualType = JsonPath.read(envelope, "$.metadata.eventType");
        assertThat(actualType).isEqualTo(eventType);
        String eventId = JsonPath.read(envelope, "$.metadata.eventId");
        assertIsUuid(eventId);
        String occurredAt = JsonPath.read(envelope, "$.metadata.occurredAt");
        assertIsInstant(occurredAt);
        Object data = JsonPath.read(envelope, "$.data");
        return toJson(data);
    }

    protected String emailSentData(String messageId, String requestedBy, String key, String templateCode,
            int version, String sentAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("requestedBy", requestedBy);
        data.put("idempotencyKey", key);
        data.put("templateCode", templateCode);
        data.put("templateVersionNumber", version);
        data.put("sentAt", sentAt);
        return toJson(data);
    }

    protected String emailFailedData(String messageId, String requestedBy, String key, String templateCode,
            int version, String failureReason, String failedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("requestedBy", requestedBy);
        data.put("idempotencyKey", key);
        data.put("templateCode", templateCode);
        data.put("templateVersionNumber", version);
        data.put("failureReason", failureReason);
        data.put("failedAt", failedAt);
        return toJson(data);
    }

    // ── Buzon ────────────────────────────────────────────────────────────────

    /** Espera a que haya {@code count} correos para la direccion y los devuelve completos. */
    protected static List<Map<String, Object>> awaitMails(String address, int count) {
        List<Map<String, Object>> mails = new ArrayList<>();
        for (String id : awaitMailTo(address, count)) {
            mails.add(mailMessage(id));
        }
        return mails;
    }

    /** Parte local del Message-ID del correo. */
    protected static String messageIdLocalPart(Map<String, Object> mail) {
        String raw = String.valueOf(mail.get("MessageID")).replace("<", "").replace(">", "").trim();
        int at = raw.indexOf('@');
        return at < 0 ? raw : raw.substring(0, at);
    }

    protected static Optional<Map<String, Object>> mailOfMessage(List<Map<String, Object>> mails, String messageId) {
        return mails.stream().filter(mail -> messageId.equals(messageIdLocalPart(mail))).findFirst();
    }

    /** El correo del mensaje: espera a que haya llegado a la direccion. */
    protected static Map<String, Object> awaitMailOfMessage(String address, String messageId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        List<Map<String, Object>> mails = new ArrayList<>();
        while (Instant.now().isBefore(deadline)) {
            mails = awaitMails(address, 1);
            Optional<Map<String, Object>> found = mailOfMessage(mails, messageId);
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Espera interrumpida", e);
            }
        }
        throw new AssertionError("No llego a " + address + " ningun correo con Message-ID de parte local " + messageId
                + ". Hay " + mails.size() + " correo(s) para esa direccion.");
    }

    /** Cuantos correos de ese mensaje hay AHORA en el buzon de esa direccion. */
    protected static long mailsOfMessage(String address, String messageId) {
        int total = mailCount(address);
        if (total == 0) {
            return 0;
        }
        return awaitMails(address, total).stream().filter(mail -> messageId.equals(messageIdLocalPart(mail))).count();
    }

    @SuppressWarnings("unchecked")
    protected static List<String> addressesOf(Map<String, Object> mail, String header) {
        Object value = mail.get(header);
        List<String> addresses = new ArrayList<>();
        if (value instanceof List<?> entries) {
            for (Object entry : entries) {
                addresses.add((String) ((Map<String, Object>) entry).get("Address"));
            }
        }
        return addresses;
    }

    @SuppressWarnings("unchecked")
    protected static String fromName(Map<String, Object> mail) {
        Map<String, Object> from = (Map<String, Object>) mail.get("From");
        return from == null ? null : (String) from.get("Name");
    }

    protected static String trimEnd(String text) {
        return text == null ? "" : text.stripTrailing();
    }
}
