package com.ecomerce.order.flows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia de los fallos, para que arbitrar un escenario no dependa de leer un
 * stack trace: al fallar un test vuelca el último intercambio HTTP completo a
 * `build/keel-failures/&lt;FL-id&gt;.json`. El agente de validación lo lee junto al XML
 * de JUnit y decide `culprit: code | test | design`.
 *
 * <p>La evidencia se guarda por hilo y, además, en un respaldo compartido: un escenario
 * de carrera hace sus peticiones desde un pool, y sin el respaldo el volcado saldría
 * vacío justo en el fallo más difícil de arbitrar.
 */
public class FailureCapture implements TestWatcher, LifecycleMethodExecutionExceptionHandler {

    private static final Path OUTPUT = Path.of("build", "keel-failures");
    private static final JsonMapper JSON = JsonMapper.builder().configureForJackson2().build();
    private static final ThreadLocal<Map<String, Object>> LAST = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> LAST_PROBE = new ThreadLocal<>();
    // Respaldo compartido: la última evidencia venga del hilo que venga.
    //
    // Los ThreadLocal aíslan los métodos de test entre sí, que es lo correcto — pero un
    // escenario de carrera (race/raceOf) hace sus peticiones desde un pool, y ahí el
    // ThreadLocal del hilo del test está vacío: el volcado saldría sin request ni
    // response y el agente de validación tendría que arbitrar a ciegas justo en el
    // escenario más difícil de arbitrar. Se lee solo como último recurso, así que el
    // aislamiento normal no cambia.
    //
    // Es correcto porque las clases de flujo NO corren en paralelo entre sí: no hay
    // junit-platform.properties con paralelismo, y el orquestador las ejecuta en serie.
    // Si algún día se activa, esto pasa a ser una fuente de evidencia cruzada y hay que
    // cambiarlo por un mapa indexado por test.
    private static final AtomicReference<Map<String, Object>> LAST_ANY = new AtomicReference<>();
    private static final AtomicReference<Map<String, Object>> LAST_PROBE_ANY = new AtomicReference<>();

    /** Registra el intercambio en curso; solo se persiste si el test falla. */
    static void record(String method, String path, HttpHeaders requestHeaders, String requestBody, AbstractFlowIT.Response response) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);
        request.put("path", path);
        request.put("headers", requestHeaders.toSingleValueMap());
        request.put("body", requestBody);

        Map<String, Object> received = new LinkedHashMap<>();
        received.put("status", response.status());
        received.put("headers", response.headers().toSingleValueMap());
        received.put("body", response.body());

        Map<String, Object> exchange = new LinkedHashMap<>();
        exchange.put("request", request);
        exchange.put("response", received);
        LAST.set(exchange);
        LAST_ANY.set(exchange);
    }

    /**
     * Registra el último sondeo de infraestructura (broker, caché) con su código de
     * salida y su salida cruda. Sin esto, un fallo de aserción sobre un evento deja
     * un volcado que solo habla de HTTP y no dice nada de por qué el canal devolvió
     * lo que devolvió — que es justo lo que hay que arbitrar.
     */
    static void recordProbe(List<String> command, int exitCode, String output) {
        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("command", String.join(" ", command));
        probe.put("exitCode", exitCode);
        probe.put("output", output);
        LAST_PROBE.set(probe);
        LAST_PROBE_ANY.set(probe);
    }

    /**
     * Marca del último volcado escrito, para que un fallo que llega DOS veces no se pise.
     *
     * <p>Un <code>@BeforeEach</code> que revienta pasa primero por
     * {@link #handleBeforeEachMethodExecutionException} —con el intercambio y el sondeo todavía
     * en memoria— y después por {@link #testFailed}, ya con {@link #clear} hecho. Sin esta
     * guarda el segundo sobrescribe el volcado bueno con uno que solo tiene la aserción: sin
     * request, sin response y sin <code>phase</code>. Un volcado vacío es peor que ninguno,
     * porque quien arbitra lo abre y no encuentra nada que contrastar con el <code>Then</code>.
     */
    private static final ThreadLocal<String> DUMPED = new ThreadLocal<>();

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String displayName = context.getDisplayName();
        String scenario = displayName.split(":")[0].trim();
        if (scenario.equals(DUMPED.get())) {
            // Ya lo volcó el handler del ciclo de vida, y con más evidencia que la que queda aquí.
            clear();
            return;
        }
        dump(scenario, displayName, context.getTestClass().map(Class::getName).orElse("?"), null, cause);
    }

    /**
     * Un <code>@BeforeAll</code> que revienta NO pasa por {@link #testFailed}: JUnit aborta el
     * contenedor de la clase, ningún método llega a ejecutarse y el TestWatcher no recibe nada.
     *
     * <p>Y es justo la clase de fallo más cara de diagnosticar —la clase entera cae con
     * <code>initializationError</code> y sus escenarios salen como NO_EJERCITADO, que dice "sin
     * cobertura" cuando lo que hubo fue un rojo—. Hasta aquí era la única que no dejaba evidencia
     * en disco, y eso pese a que {@link #recordProbe} ya tenía en memoria el comando, su código de
     * salida y su salida completa en el instante del fallo.
     */
    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        String testClass = context.getTestClass().map(Class::getName).orElse("?");
        String simpleName = testClass.substring(testClass.lastIndexOf('.') + 1);
        dump(simpleName + "-init", context.getDisplayName(), testClass, "@BeforeAll", throwable);
        // Capturar no es tragar: se relanza para que el desenlace de la clase no cambie.
        throw throwable;
    }

    /**
     * Un <code>@BeforeEach</code> que revienta tampoco tiene garantizado el paso por
     * {@link #testFailed} — depende de la versión de JUnit—, y a diferencia del de
     * <code>@BeforeAll</code> aquí SÍ hay escenario: el contexto es el del método de prueba, así
     * que el volcado va a su <code>FL-id</code> y no a un <code>&lt;Clase&gt;-init</code>.
     *
     * <p>Es la mitad que faltaba de {@code awaitPreconditions}: sacar una espera del
     * <code>@BeforeAll</code> hace que el fallo se atribuya al escenario, pero sin esto el
     * escenario saldría FALLO <b>sin evidencia</b>, y la evidencia es justo lo que arbitra
     * {@code keel-spring-validate}.
     */
    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        String displayName = context.getDisplayName();
        dump(displayName.split(":")[0].trim(), displayName,
                context.getTestClass().map(Class::getName).orElse("?"), "@BeforeEach", throwable);
        throw throwable;
    }

    /** Vuelca el informe de un fallo con la evidencia que haya en memoria y limpia. */
    private static void dump(String scenario, String displayName, String testClass, String phase, Throwable cause) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scenario", scenario);
        report.put("displayName", displayName);
        report.put("testClass", testClass);
        if (phase != null) {
            report.put("phase", phase);
        }
        report.put("assertion", Optional.ofNullable(cause.getMessage()).orElse(cause.toString()));
        Map<String, Object> exchange = Optional.ofNullable(LAST.get()).orElseGet(LAST_ANY::get);
        if (exchange != null) {
            report.putAll(exchange);
        }
        Map<String, Object> probe = Optional.ofNullable(LAST_PROBE.get()).orElseGet(LAST_PROBE_ANY::get);
        if (probe != null) {
            report.put("probe", probe);
        }
        write(scenario, report);
        clear();
        DUMPED.set(scenario);
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        clear();
    }

    private static void clear() {
        DUMPED.remove();
        LAST.remove();
        LAST_PROBE.remove();
        LAST_ANY.set(null);
        LAST_PROBE_ANY.set(null);
    }

    private static void write(String scenario, Map<String, Object> report) {
        String name = scenario.replaceAll("[^A-Za-z0-9_.-]", "_");
        try {
            Files.createDirectories(OUTPUT);
            Files.writeString(
                OUTPUT.resolve((name.isEmpty() ? "unnamed" : name) + ".json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
