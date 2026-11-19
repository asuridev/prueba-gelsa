package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;

/**
 * Fixtures compartidos de las clases de flujo de order: catálogo base, compradores de
 * prueba, doble de payment, alta/lectura de pedidos y lectura de los correos encargados.
 *
 * <p>No es fontanería del arnés (eso sigue en {@link AbstractFlowIT}, que no se toca): es el
 * vocabulario del documento de escenarios (`P1`, «el token de ana», «se encarga el correo
 * X») escrito una sola vez. Es abstracta y no acaba en {@code IT}: JUnit no la ejecuta.
 */
abstract class OrderFlowSupport extends AbstractFlowIT {

    // ── Datos de prueba del documento de escenarios ──────────────────────────

    protected static final String P1 = "aaaaaaaa-0000-4000-8000-000000000001";
    protected static final String P2 = "aaaaaaaa-0000-4000-8000-000000000002";
    protected static final String P3 = "aaaaaaaa-0000-4000-8000-000000000003";
    protected static final String P9 = "aaaaaaaa-0000-4000-8000-000000000009";
    protected static final String PAY1 = "bbbbbbbb-0000-4000-8000-000000000001";
    protected static final String PAY2 = "bbbbbbbb-0000-4000-8000-000000000002";
    protected static final String MISSING_ORDER = "dddddddd-0000-4000-8000-000000000000";
    protected static final String ORDERS = ROUTE_BASE + "/orders";
    protected static final String NOTIFICATIONS = "notificationRequests";
    protected static final String REDIRECT_3DS_1 = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/1\"}";

    // ── Compradores (el token se pide en cada petición, nunca se guarda) ─────

    protected String ana() {
        return tokenAs("ana", Map.of("email", "ana@example.com"));
    }

    protected String luis() {
        return tokenAs("luis", Map.of("email", "luis@example.com"));
    }

    /** `sub: eva` sin claim email. */
    protected String evaWithoutEmail() {
        Map<String, String> claims = new HashMap<>();
        claims.put("email", null);
        return tokenAs("eva", claims);
    }

    /** `sub: eva` con un email que no lo es. */
    protected String evaWithBadEmail() {
        return tokenAs("eva", Map.of("email", "no-es-un-email"));
    }

    protected static String emailOf(String customer) {
        return customer + "@example.com";
    }

    // ── Catálogo ─────────────────────────────────────────────────────────────

    protected static String productJson(String id, String sku, String name, String price, String currency, long version) {
        return "{\"productId\":\"" + id + "\",\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"price\":" + price
                + ",\"currency\":\"" + currency + "\",\"version\":" + version + "}";
    }

    /** «El catálogo base»: P1, P2 y P3 con version 1, entregados y aplicados. */
    protected void seedCatalog() {
        deliverProductUpserted("e-p1-v1", productJson(P1, "SKU-001", "Camiseta", "12.50", "EUR", 1));
        deliverProductUpserted("e-p2-v1", productJson(P2, "SKU-002", "Gorra", "7.25", "EUR", 1));
        deliverProductUpserted("e-p3-v1", productJson(P3, "SKU-003", "Libro", "20.00", "USD", 1));
        awaitProductVersion(P1, 1);
        awaitProductVersion(P2, 1);
        awaitProductVersion(P3, 1);
    }

    /** Versión que guarda la copia local del producto, o -1 si no hay fila. */
    protected long productVersionOf(String productId) {
        String output = db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "order", "-d", "order",
                "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c",
                "SELECT source_version FROM products WHERE id = " + uuidLiteral(productId));
        String last = "";
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                last = line.trim();
            }
        }
        return last.isEmpty() ? -1 : Long.parseLong(last);
    }

    protected void awaitProductVersion(String productId, long version) {
        await(Duration.ofSeconds(30), () -> productVersionOf(productId) == version);
    }

    // ── Doble de payment ─────────────────────────────────────────────────────

    protected static String q(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    protected static String raw(String json) {
        return json == null ? "null" : json;
    }

    protected static String chargeJson(String id, String status, String failureReason, String customerActionJson) {
        return "{\"id\":" + q(id) + ",\"chargeRequestId\":\"order:stub\",\"status\":\"" + status
                + "\",\"failureReason\":" + q(failureReason) + ",\"customerAction\":" + raw(customerActionJson) + "}";
    }

    /** payment responde a `requestCharge` con `201` y ese cobro. */
    protected static void stubCharge(String status, String paymentId, String failureReason, String customerActionJson) {
        stubFor("POST", "/payments", 201, chargeJson(paymentId, status, failureReason, customerActionJson));
    }

    protected static void stubCapture(String paymentId, String status) {
        stubFor("POST", "/payments/" + paymentId + "/capture", 200,
                "{\"id\":\"" + paymentId + "\",\"status\":\"" + status + "\"}");
    }

    protected static void stubCancel(String paymentId, String status, String failureReason) {
        stubFor("POST", "/payments/" + paymentId + "/cancel", 200,
                "{\"id\":\"" + paymentId + "\",\"status\":\"" + status + "\",\"failureReason\":" + q(failureReason) + "}");
    }

    protected static String errorJson(String code) {
        return "{\"status\":0,\"error\":\"error\",\"code\":\"" + code + "\",\"message\":\"" + code + "\"}";
    }

    protected static final String QUERY_ANY = "/payments/by-charge-request/.*";

    protected static String queryOf(String orderId) {
        return "/payments/by-charge-request/order(:|%3A)" + orderId;
    }

    protected static void stubQuery(String pathPattern, String paymentId, String status) {
        stubFor("GET", pathPattern, 200, chargeJson(paymentId, status, null, null));
    }

    protected static int capturesOf(String paymentId) {
        return stubCallCount("POST", "/payments/" + paymentId + "/capture");
    }

    protected static int cancelsOf(String paymentId) {
        return stubCallCount("POST", "/payments/" + paymentId + "/cancel");
    }

    protected static int charges() {
        return stubCallCount("POST", "/payments");
    }

    /**
     * Respuesta del doble CON retardo. La base ({@code stubFor}) no sabe de retardos y
     * {@code StubResponse.timeout} devuelve un cuerpo vacío: aquí hace falta una respuesta
     * válida que tarde (FL-ORD-008, FL-CAN-006). Habla con el mismo admin API que la base.
     */
    protected void stubForDelayed(String method, String pathPattern, int status, String jsonBody, int delayMs) {
        String mapping = "{\"request\":{\"method\":\"" + method + "\",\"urlPathPattern\":" + toJson(pathPattern) + "},"
                + "\"response\":{\"status\":" + status + ",\"fixedDelayMilliseconds\":" + delayMs
                + ",\"headers\":{\"Content-Type\":\"application/json\"},\"body\":" + toJson(jsonBody) + "}}";
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:8090/__admin/mappings"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapping))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("mapping con retardo: " + response.body()).isLessThan(300);
        } catch (IOException e) {
            throw new AssertionError("No se pudo programar el stub con retardo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrumpido programando el stub", e);
        }
    }

    // ── Pedidos ──────────────────────────────────────────────────────────────

    protected static String line(String productId, int quantity) {
        return "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
    }

    protected static String paymentToken(String token) {
        return "\"paymentToken\":\"" + token + "\"";
    }

    protected static String paymentMethod(String id) {
        return "\"paymentMethodId\":\"" + id + "\"";
    }

    protected static final String TOKEN_OK = paymentToken("tok_visa_ok");

    /** Cuerpo de createOrder; {@code payment} vacío = sin fuente de pago. */
    protected static String orderBody(String payment, String... lines) {
        return "{\"lines\":[" + String.join(",", lines) + "]" + (payment.isEmpty() ? "" : "," + payment) + "}";
    }

    protected Response createOrder(String token, String key, String body) {
        return exchangeWithKey(HttpMethod.POST, ORDERS, body, token, key);
    }

    /** createOrder que tiene que dar 201; devuelve la respuesta. */
    protected Response createdOrder(String token, String key, String body) {
        Response response = createOrder(token, key, body);
        assertThat(response.status()).as("alta de pedido (siembra): " + response.body()).isEqualTo(201);
        return response;
    }

    protected String idOf(Response response) {
        String id = jsonPath(response, "$.id");
        assertIsUuid(id);
        return id;
    }

    protected Response getOrder(String token, String id) {
        return get(ORDERS + "/" + id, token);
    }

    protected Response cancelOrder(String token, String id) {
        return post(ORDERS + "/" + id + "/cancel", null, token);
    }

    protected String statusOfAna(String id) {
        Response response = getOrder(ana(), id);
        if (response.status() != 200) {
            return "HTTP-" + response.status();
        }
        String status = jsonPath(response, "$.status");
        return status;
    }

    protected void awaitStatusAna(String id, String status) {
        await(Duration.ofSeconds(45), () -> status.equals(statusOfAna(id)));
    }

    /** Estado leído; afirma status 200. */
    protected String statusAssertedAna(String id) {
        Response response = getOrder(ana(), id);
        assertThat(response.status()).isEqualTo(200);
        String status = jsonPath(response, "$.status");
        return status;
    }

    protected void assertError(Response response, int status, String code) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        String actual = jsonPath(response, "$.code");
        assertThat(actual).isEqualTo(code);
    }

    protected BigDecimal number(Response response, String path) {
        Number value = jsonPath(response, path);
        return new BigDecimal(value.toString());
    }

    protected void assertAmount(BigDecimal actual, String expected) {
        assertThat(actual.compareTo(new BigDecimal(expected))).as(actual + " = " + expected).isZero();
    }

    /** Una línea esperada de la proyección de Order. */
    protected record L(String productId, String sku, String name, String price, int quantity) {
    }

    protected static L p1(int quantity) {
        return new L(P1, "SKU-001", "Camiseta", "12.50", quantity);
    }

    protected static L p2(int quantity) {
        return new L(P2, "SKU-002", "Gorra", "7.25", quantity);
    }

    /**
     * La proyección de Order completa en STRICT: exactamente los campos del contrato (sin
     * las marcas de espera ni versión). Las líneas se comparan como conjunto por
     * {@code productId}: se reordenan las esperadas al orden en que las devolvió el servidor.
     * `customerEmail` sale de `customer`; `lineCount` de las líneas esperadas.
     */
    protected void assertOrder(Response response, String id, String customer, String status, String currency,
            String total, String paymentId, String customerActionJson, String failureReason, L... expectedLines) {
        assertThat(response.status()).as(response.body()).isIn(200, 201);
        String createdAt = jsonPath(response, "$.createdAt");
        String updatedAt = jsonPath(response, "$.updatedAt");
        assertIsInstant(createdAt);
        assertIsInstant(updatedAt);
        List<Map<String, Object>> actualLines = jsonPath(response, "$.lines");
        assertThat(actualLines).hasSize(expectedLines.length);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> actual : actualLines) {
            Object lineId = actual.get("id");
            assertIsUuid(String.valueOf(lineId));
            Object productId = actual.get("productId");
            L expected = null;
            for (L candidate : expectedLines) {
                if (candidate.productId().equals(String.valueOf(productId))) {
                    expected = candidate;
                }
            }
            assertThat(expected).as("línea inesperada " + productId).isNotNull();
            lines.add("{\"id\":\"" + lineId + "\",\"orderId\":\"" + id + "\",\"productId\":\"" + expected.productId()
                    + "\",\"sku\":\"" + expected.sku() + "\",\"productName\":\"" + expected.name()
                    + "\",\"unitPrice\":" + expected.price() + ",\"quantity\":" + expected.quantity() + "}");
        }
        String expectedJson = "{\"id\":\"" + id + "\",\"customerRef\":\"" + customer + "\",\"customerEmail\":\""
                + emailOf(customer) + "\",\"status\":\"" + status + "\",\"currency\":\"" + currency
                + "\",\"totalAmount\":" + total + ",\"lineCount\":" + expectedLines.length
                + ",\"paymentId\":" + q(paymentId) + ",\"customerAction\":" + raw(customerActionJson)
                + ",\"failureReason\":" + q(failureReason) + ",\"createdAt\":\"" + createdAt
                + "\",\"updatedAt\":\"" + updatedAt + "\",\"lines\":[" + String.join(",", lines) + "]}";
        assertBody(response, expectedJson);
    }

    /** El cuerpo saliente de la petición de cobro: campos obligatorios y fuente de pago. */
    protected void assertChargeBody(String requestJson, String orderId, String customer, String amount,
            String currency, String paymentTokenOrNull, String paymentMethodOrNull) {
        String body = stubRequestBody(requestJson);
        Map<String, Object> fields = JsonPath.read(body, "$");
        assertThat(fields.get("chargeRequestId")).isEqualTo("order:" + orderId);
        assertThat(fields.get("customerRef")).isEqualTo(customer);
        assertThat(fields.get("currency")).isEqualTo(currency);
        Object sent = fields.get("amount");
        assertThat(sent).isInstanceOf(Number.class);
        assertAmount(new BigDecimal(sent.toString()), amount);
        assertThat(fields.get("paymentToken")).isEqualTo(paymentTokenOrNull);
        assertThat(fields.get("paymentMethodId")).isEqualTo(paymentMethodOrNull);
    }

    // ── Eventos de payment ───────────────────────────────────────────────────

    protected static String paymentEvent(String paymentId, String orderId, String customer, String amount,
            String currency, String extraFields) {
        return "{\"paymentId\":\"" + paymentId + "\",\"chargeRequestId\":\"order:" + orderId
                + "\",\"customerRef\":\"" + customer + "\",\"amount\":" + amount + ",\"currency\":\"" + currency + "\""
                + (extraFields == null || extraFields.isEmpty() ? "" : "," + extraFields) + "}";
    }

    protected static String authorizedEvent(String orderId, String amount, String gatewayId) {
        return paymentEvent(PAY1, orderId, "ana", amount, "EUR", "\"gatewayPaymentId\":\"" + gatewayId + "\"");
    }

    /** El pedido no cambia durante toda una ventana (comparado por cuerpo completo). */
    protected void assertOrderUnchanged(String orderId, String snapshotBody) {
        holdsFor(Duration.ofSeconds(5), () -> snapshotBody.equals(getOrder(ana(), orderId).body()));
    }

    // ── Correos encargados (canal notificationRequests) ──────────────────────

    /** Cada NotificationRequested del canal, como su sobre completo en JSON. */
    protected List<String> notificationEnvelopes() {
        String raw = publishedMessages(NOTIFICATIONS, 500);
        List<Object> payloads = JsonPath.read(raw, "$[*].payload");
        List<String> envelopes = new ArrayList<>();
        for (Object payload : payloads) {
            String json = toJson(payload);
            String type = JsonPath.read(json, "$.metadata.eventType");
            if ("NotificationRequested".equals(type)) {
                envelopes.add(json);
            }
        }
        return envelopes;
    }

    protected List<String> notificationsWithKey(String idempotencyKey) {
        List<String> matching = new ArrayList<>();
        for (String envelope : notificationEnvelopes()) {
            String key = JsonPath.read(envelope, "$.data.idempotencyKey");
            if (idempotencyKey.equals(key)) {
                matching.add(envelope);
            }
        }
        return matching;
    }

    protected int notificationCountOf(String orderId) {
        int count = 0;
        for (String envelope : notificationEnvelopes()) {
            String key = JsonPath.read(envelope, "$.data.idempotencyKey");
            if (key.startsWith("order:" + orderId + ":")) {
                count++;
            }
        }
        return count;
    }

    /** Ningún correo encargado durante la ventana. */
    protected void assertNoEmails(Duration window) {
        holdsFor(window, () -> notificationEnvelopes().isEmpty());
    }

    /**
     * «Se encarga el correo X»: exactamente un NotificationRequested con la clave
     * `order:<id>:<desenlace>`, `metadata.source: order` y el payload del contrato.
     */
    protected void assertEmailRequested(String orderId, String outcome, String template, String customer,
            String total, String currency, String failureReason) {
        String key = "order:" + orderId + ":" + outcome;
        await(Duration.ofSeconds(25), () -> !notificationsWithKey(key).isEmpty());
        holdsFor(Duration.ofSeconds(2), () -> notificationsWithKey(key).size() == 1);
        String envelope = notificationsWithKey(key).get(0);
        String source = JsonPath.read(envelope, "$.metadata.source");
        assertThat(source).isEqualTo("order");
        String template0 = JsonPath.read(envelope, "$.data.templateCode");
        assertThat(template0).isEqualTo(template);
        List<String> recipients = JsonPath.read(envelope, "$.data.recipients");
        assertThat(recipients).containsExactly(emailOf(customer));
        List<Map<String, Object>> variables = JsonPath.read(envelope, "$.data.variables");
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map<String, Object> variable : variables) {
            actual.put(String.valueOf(variable.get("name")), String.valueOf(variable.get("value")));
        }
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("orderId", orderId);
        expected.put("totalAmount", total);
        expected.put("currency", currency);
        if (failureReason != null) {
            expected.put("failureReason", failureReason);
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    protected void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Pausa interrumpida", e);
        }
    }

    /** Un ciclo del barrido: adelanta el temporizador (lo toma una réplica en su próxima pasada). */
    protected void sweepCycle() {
        fireTimer("schedule:reconcileStalledOrders");
    }
}
