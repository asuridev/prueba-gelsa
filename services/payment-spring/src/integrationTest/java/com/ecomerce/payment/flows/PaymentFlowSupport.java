package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;

/**
 * Fixtures de dominio compartidos por las clases de flujo de payment: siembra de cobros y medios,
 * proyecciones esperadas, lectura de eventos del canal y conteo de llamadas a la pasarela de prueba.
 *
 * <p>No es fontaneria del arnes (eso es {@link AbstractFlowIT}, de solo lectura): es el vocabulario
 * del servicio escrito una vez. Todo sale del contrato (specs, openapi.yaml, asyncapi.yaml); nada de
 * aqui lee el codigo del servidor.
 */
abstract class PaymentFlowSupport extends AbstractFlowIT {

    protected static final String EVENTS = "paymentEvents";

    /** Marca de «este campo viene con valor»: se sustituye por el valor real antes del STRICT. */
    protected static final Object NOT_NULL = new Object() {
        @Override
        public String toString() {
            return "NOT_NULL";
        }
    };

    // ── Credenciales (se piden en cada peticion, nunca se guardan) ───────────

    protected String checkoutToken() {
        return serviceCredential("checkout");
    }

    protected String backofficeToken() {
        return serviceCredential("backoffice");
    }

    // ── Peticiones ───────────────────────────────────────────────────────────

    protected static String tokenSource() {
        return "\"paymentToken\": \"tok_" + UUID.randomUUID() + "\"";
    }

    protected static String methodSource(String paymentMethodId) {
        return "\"paymentMethodId\": \"" + paymentMethodId + "\"";
    }

    protected Response charge(String ref, String customer, String amount, String currency, String source) {
        String extra = source == null ? "" : ", " + source;
        String body = "{\"chargeRequestId\": \"%s\", \"customerRef\": \"%s\", \"amount\": %s, \"currency\": \"%s\"%s}"
                .formatted(ref, customer, amount, currency, extra);
        return post(ROUTE_BASE + "/payments", body, checkoutToken());
    }

    protected Response charge(String ref) {
        return charge(ref, "cus-1", "10.00", "EUR", tokenSource());
    }

    protected Response readPayment(String id) {
        return get(ROUTE_BASE + "/payments/" + id, checkoutToken());
    }

    protected Response readByCharge(String ref) {
        return get(ROUTE_BASE + "/payments/by-charge-request/" + ref, checkoutToken());
    }

    protected Response capture(String id) {
        return post(ROUTE_BASE + "/payments/" + id + "/capture", null, checkoutToken());
    }

    protected Response cancel(String id) {
        return post(ROUTE_BASE + "/payments/" + id + "/cancel", null, checkoutToken());
    }

    protected Response refund(String id, String body) {
        return post(ROUTE_BASE + "/payments/" + id + "/refunds", body, backofficeToken());
    }

    protected Response listPayments(String query) {
        return get(ROUTE_BASE + "/payments?" + query, checkoutToken());
    }

    protected Response listMethods(String customer) {
        return get(ROUTE_BASE + "/payment-methods?customerRef=" + customer, checkoutToken());
    }

    protected Response saveMethod(String customer, String key, String token, String extra) {
        String body = "{\"customerRef\": \"%s\", \"paymentToken\": \"%s\"%s}"
                .formatted(customer, token, extra == null ? "" : ", " + extra);
        return exchangeWithKey(HttpMethod.POST, ROUTE_BASE + "/payment-methods", body, checkoutToken(), key);
    }

    protected Response setDefault(String methodId, String customer) {
        return post(ROUTE_BASE + "/payment-methods/" + methodId + "/default",
                "{\"customerRef\": \"" + customer + "\"}", checkoutToken());
    }

    protected Response removeMethod(String methodId, String customer) {
        return delete(ROUTE_BASE + "/payment-methods/" + methodId + "?customerRef=" + customer, checkoutToken());
    }

    // ── Lectura de respuestas ────────────────────────────────────────────────

    protected String idOf(Response response) {
        String id = jsonPath(response, "$.id");
        assertIsUuid(id);
        return id;
    }

    protected String statusOf(String paymentId) {
        Response response = readPayment(paymentId);
        String status = jsonPath(response, "$.status");
        return status;
    }

    protected void awaitStatus(String paymentId, String status) {
        await(Duration.ofSeconds(90), () -> status.equals(statusOf(paymentId)));
    }

    protected void assertError(Response response, int status, String code) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        String actual = jsonPath(response, "$.code");
        assertThat(actual).as(response.body()).isEqualTo(code);
    }

    // ── Siembra del Given (cada una comprueba su propio status) ──────────────

    private void assertSeed(Response response, int http, String status, String ref) {
        assertThat(response.status()).as("siembra de %s: %s", ref, response.body()).isEqualTo(http);
        String actual = jsonPath(response, "$.status");
        assertThat(actual).as("estado de %s tras la siembra: %s", ref, response.body()).isEqualTo(status);
    }

    protected String seedAuthorized(String ref, String customer, String amount) {
        gatewayAuthorizes(ref);
        Response response = charge(ref, customer, amount, "EUR", tokenSource());
        assertSeed(response, 201, "authorized", ref);
        return idOf(response);
    }

    protected String seedAuthorized(String ref) {
        return seedAuthorized(ref, "cus-1", "10.00");
    }

    protected String seedCaptured(String ref, String customer, String amount) {
        String id = seedAuthorized(ref, customer, amount);
        gatewayCaptures(ref);
        assertSeed(capture(id), 200, "captured", ref);
        return id;
    }

    protected String seedCaptured(String ref) {
        return seedCaptured(ref, "cus-1", "10.00");
    }

    protected String seedActionRequired(String ref) {
        gatewayRequiresAction(ref);
        Response response = charge(ref);
        assertSeed(response, 201, "actionRequired", ref);
        return idOf(response);
    }

    /** Un cobro en pending: la pasarela corta la conexion al cobrar (queda registrado, sin respuesta). */
    protected String seedPending(String ref) {
        gatewayDoesNotAnswer(GatewayCall.CHARGE);
        Response response = charge(ref);
        assertSeed(response, 201, "pending", ref);
        return idOf(response);
    }

    protected static String newPmId() {
        return "pm_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** La pasarela guarda el medio con esa tarjeta (cliente + vinculacion, con la forma de Stripe). */
    protected static void gatewaySavesCard(String brand, String last4, int month, int year, String id) {
        String card = "{\"id\": \"%s\", \"object\": \"payment_method\", \"type\": \"card\", \"card\": "
                + "{\"brand\": \"%s\", \"last4\": \"%s\", \"exp_month\": %s, \"exp_year\": %s}}";
        stubFor("POST", "/v1/customers", 200, "{\"id\": \"cus_keel_test\", \"object\": \"customer\"}");
        stubFor("POST", "/v1/payment_methods/[^/]+/attach", 200,
                card.formatted(id, brand, last4, String.valueOf(month), String.valueOf(year)));
    }

    protected static void gatewaySavesVisa() {
        gatewaySavesCard("visa", "4242", 12, 2030, newPmId());
    }

    protected String seedMethod(String customer) {
        gatewaySavesVisa();
        Response response = saveMethod(customer, "k-" + UUID.randomUUID(), "tok_" + UUID.randomUUID(), null);
        assertThat(response.status()).as("siembra del medio de %s: %s", customer, response.body()).isEqualTo(201);
        return idOf(response);
    }

    // ── Proyecciones esperadas ───────────────────────────────────────────────

    protected static Map<String, Object> exp(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** Un cobro con el valor que le toca a cada campo en el estado base; el escenario pisa lo suyo. */
    protected static Map<String, Object> payment(Object... overrides) {
        Map<String, Object> map = exp(
                "id", NOT_NULL,
                "chargeRequestId", null,
                "customerRef", null,
                "amount", null,
                "currency", null,
                "status", null,
                "gatewayPaymentId", null,
                "awaitingSince", null,
                "failureReason", null,
                "customerAction", null,
                "actionRequiredSince", null,
                "cancelOrigin", null,
                "paymentMethodId", null,
                "refundedAmount", 0,
                "createdAt", NOT_NULL,
                "updatedAt", NOT_NULL,
                "refunds", new ArrayList<>());
        map.putAll(exp(overrides));
        return map;
    }

    protected static Map<String, Object> refundItem(String refundRequestId, double amount, String reason,
            String status, String paymentId) {
        return exp("id", NOT_NULL, "refundRequestId", refundRequestId, "amount", amount, "reason", reason,
                "status", status, "createdAt", NOT_NULL, "updatedAt", NOT_NULL, "paymentId", paymentId);
    }

    protected static Map<String, Object> methodItem(Object id, String brand, String last4, int month, int year,
            boolean isDefault, boolean expired, Object walletId) {
        return exp("id", id,
                "card", exp("brand", brand, "last4", last4, "expMonth", month, "expYear", year),
                "isDefault", isDefault, "status", "active", "expired", expired, "walletId", walletId);
    }

    /** Sustituye cada NOT_NULL por el valor real (comprobando que lo hay) y deja el resto tal cual. */
    protected Object resolve(Object expected, Object actual) {
        if (expected == NOT_NULL) {
            assertThat(actual).as("campo que debia venir con valor").isNotNull();
            return actual;
        }
        if (expected instanceof Map<?, ?> map) {
            Map<?, ?> actualMap = actual instanceof Map<?, ?> am ? am : Map.of();
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), resolve(entry.getValue(), actualMap.get(entry.getKey())));
            }
            return out;
        }
        if (expected instanceof List<?> list) {
            List<?> actualList = actual instanceof List<?> al ? al : List.of();
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                out.add(resolve(list.get(i), i < actualList.size() ? actualList.get(i) : null));
            }
            return out;
        }
        return expected;
    }

    /** Status + cuerpo completo en STRICT contra la proyeccion esperada (ids e instantes, por forma). */
    protected void assertPayment(Response response, int status, Map<String, Object> expected) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        Object actual = JsonPath.read(response.body(), "$");
        assertJson(response.body(), toJson(resolve(expected, actual)));
        String id = jsonPath(response, "$.id");
        String createdAt = jsonPath(response, "$.createdAt");
        String updatedAt = jsonPath(response, "$.updatedAt");
        assertIsUuid(id);
        assertIsInstant(createdAt);
        assertIsInstant(updatedAt);
    }

    protected void assertMethod(Response response, int status, Map<String, Object> expected) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        Object actual = JsonPath.read(response.body(), "$");
        assertJson(response.body(), toJson(resolve(expected, actual)));
    }

    protected void assertPage(Response response, int page, int size, long total, int pages,
            List<Map<String, Object>> items) {
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        Map<String, Object> expected = exp("items", items, "page", page, "size", size, "totalElements", total,
                "totalPages", pages);
        Object actual = JsonPath.read(response.body(), "$");
        assertJson(response.body(), toJson(resolve(expected, actual)));
    }

    // ── Eventos del canal ────────────────────────────────────────────────────

    /** Todas las envolturas {metadata, data} del tipo dado para ese cobro. */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> eventsOf(String type, String ref) {
        String messages = publishedMessages(EVENTS, 500);
        List<Map<String, Object>> payloads = JsonPath.read(messages, "$[*].payload");
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> envelope : payloads) {
            Map<String, Object> meta = (Map<String, Object>) envelope.get("metadata");
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            if (meta != null && data != null && type.equals(meta.get("eventType"))
                    && ref.equals(data.get("chargeRequestId"))) {
                found.add(envelope);
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> eventsOf(String type, String ref, String field, Object value) {
        List<Map<String, Object>> all = eventsOf(type, ref);
        if (field == null) {
            return all;
        }
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> envelope : all) {
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            if (value.equals(data.get(field))) {
                found.add(envelope);
            }
        }
        return found;
    }

    /** Espera a que lleguen al menos {@code expected} y comprueba que son EXACTAMENTE esos. */
    protected List<Map<String, Object>> awaitEvents(String type, String ref, String field, Object value, int expected) {
        if (expected > 0) {
            await(Duration.ofSeconds(20), () -> eventsOf(type, ref, field, value).size() >= expected);
        }
        List<Map<String, Object>> found = eventsOf(type, ref, field, value);
        assertThat(found).as("eventos %s de %s", type, ref).hasSize(expected);
        return found;
    }

    protected List<Map<String, Object>> awaitEvents(String type, String ref, int expected) {
        return awaitEvents(type, ref, null, null, expected);
    }

    /** Exactamente un evento de ese tipo, con ese {@code data} completo (STRICT). */
    protected void assertEvent(String type, String ref, Map<String, Object> data) {
        assertEvent(type, ref, null, null, data);
    }

    protected void assertEvent(String type, String ref, String field, Object value, Map<String, Object> data) {
        List<Map<String, Object>> found = awaitEvents(type, ref, field, value, 1);
        Object actual = found.get(0).get("data");
        assertJson(toJson(actual), toJson(resolve(data, actual)));
    }

    protected static Map<String, Object> eventData(String paymentId, String ref, String customer, double amount,
            Object... extra) {
        Map<String, Object> map = exp("paymentId", paymentId, "chargeRequestId", ref, "customerRef", customer,
                "amount", amount, "currency", "EUR");
        map.putAll(exp(extra));
        return map;
    }

    /** El {@code data} de PaymentActionRejected: una acción de seguimiento que la pasarela no hizo. */
    protected static Map<String, Object> rejectedData(String paymentId, String ref, String action,
            String refundRequestId, Double refundAmount, String status) {
        return exp("paymentId", paymentId, "chargeRequestId", ref, "customerRef", "cus-1", "action", action,
                "refundRequestId", refundRequestId, "refundAmount", refundAmount, "status", status);
    }

    // ── Llamadas a la pasarela de prueba ─────────────────────────────────────

    protected static long chargeCalls(String ref) {
        return gatewayRequests(GatewayCall.CHARGE).stream()
                .filter(request -> stubRequestBody(request).contains(ref)).count();
    }

    protected static long refundCalls(String ref) {
        return gatewayRequests(GatewayCall.REFUND).stream()
                .filter(request -> stubRequestBody(request).contains(gatewayIdFor(ref))).count();
    }

    protected static int captureCalls(String ref) {
        return stubCallCount("POST", "/v1/payment_intents/" + gatewayIdFor(ref) + "/capture");
    }

    protected static int cancelCalls(String ref) {
        return stubCallCount("POST", "/v1/payment_intents/" + gatewayIdFor(ref) + "/cancel");
    }

    /** Consultas de estado de ese cobro (por id o por la busqueda de su referencia). */
    protected static long statusQueries(String ref) {
        return stubRequests("GET", "/v1/payment_intents/.+").stream().filter(request -> {
            String url = JsonPath.read(request, "$.url");
            return url.contains(gatewayIdFor(ref)) || url.contains(ref);
        }).count();
    }

    protected static int attachCalls() {
        return gatewayCallCount(GatewayCall.ATTACH_METHOD);
    }

    // ── Reloj del barrido ────────────────────────────────────────────────────

    protected static void sql(String statement) {
        db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment",
                "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", statement);
    }

    /** Envejece la marca de espera de esa fila (como {@code ageForReconciliation}, sin disparar el barrido). */
    protected static void ageAwaiting(String paymentId) {
        sql("UPDATE payments SET awaiting_since = TIMESTAMP '1970-01-01 00:00:00' WHERE id = " + uuidLiteral(paymentId));
    }

    /** Envejece el reloj del plazo del 3DS de esa fila. */
    protected static void ageActionRequired(String paymentId) {
        sql("UPDATE payments SET action_required_since = TIMESTAMP '1970-01-01 00:00:00' WHERE id = "
                + uuidLiteral(paymentId));
    }

    /** Adelanta un ciclo de {@code sweepPendingPayments}: lo ejecuta UNA replica en su proxima pasada. */
    protected static void runSweep() {
        fireTimer("schedule:sweepPendingPayments");
    }
}
