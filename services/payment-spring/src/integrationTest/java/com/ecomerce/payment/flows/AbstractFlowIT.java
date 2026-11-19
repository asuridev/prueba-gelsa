package com.ecomerce.payment.flows;

import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las clases de flujo (`<Flow>FlowIT`) que ejecutan los escenarios FL-*
 * de specs/validation-scenarios.md contra el servidor real y la infraestructura
 * de infra/docker-compose.yaml.
 *
 * <p><b>Caja negra.</b> build.gradle deja src/main/java fuera del compileClasspath
 * de este source set: aquí no se puede importar ningún DTO, comando ni entidad del
 * servicio, y un test que lo necesitase tendría mal planteada la aserción. Los
 * escenarios se expresan solo con HTTP y JSON, que es lo que exige el contrato de
 * equivalencia entre stacks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Boot 4 ya no registra el TestRestTemplate por estar en un @SpringBootTest: se pide.
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(FailureCapture.class)
public abstract class AbstractFlowIT {

    /** Prefijo de todas las rutas del servicio (basePath del diseño + versión). */
    protected static final String ROUTE_BASE = "/api/v1";

    /**
     * Re-serializa a JSON los nodos que devuelve JsonPath. Ver {@link #toJson}. Con los defaults
     * de Jackson 2, los mismos que el mapper del servidor, para que el arnés lea y escriba el
     * JSON como lo hacía antes de Jackson 3.
     */
    private static final JsonMapper JSON = JsonMapper.builder().configureForJackson2().build();

    @Autowired
    protected TestRestTemplate rest;

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Cuánta vida le tiene que quedar a un token cacheado para seguir sirviendo. Cubre lo que
     * tarde la petición en llegar y el desfase de reloj con el proveedor de identidad.
     */
    private static final Duration TOKEN_RENEWAL_MARGIN = Duration.ofSeconds(30);

    /** El claim {@code exp} dentro del payload del JWT, leído sin librerías de por medio. */
    private static final java.util.regex.Pattern EXP_CLAIM =
            java.util.regex.Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    @BeforeAll
    void configureHttpClient() {
        // El factory por defecto (HttpURLConnection) no soporta PATCH; el del
        // HttpClient del JDK sí, y no añade dependencias.
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    /**
     * Red de CIERRE para la réplica. La otra —{@code resetState()}— es de APERTURA, y por eso no
     * basta: para la réplica al empezar la clase SIGUIENTE, así que no llega nunca si la clase de
     * clúster es la última de la suite, ni si la siguiente corre en otro fork de Gradle.
     *
     * <p>Es idempotente: sobre una réplica ya parada no hace nada, así que ponerla en TODAS las
     * clases no cuesta nada y cierra el hueco sin que ningún escenario tenga que acordarse de su
     * {@code finally}.
     */
    @AfterAll
    void pararLaReplicaAlCerrarLaClase() {
        stopReplica();
    }

    /** Intercambio HTTP completo: lo que se asserta y lo que se vuelca al fallar. */
    public record Response(int status, HttpHeaders headers, String body) {
        public String header(String name) {
            return headers.getFirst(name);
        }
    }

    // ── Llamadas HTTP ────────────────────────────────────────────────────────

    protected Response get(String path) {
        return exchange(HttpMethod.GET, path, null, null);
    }

    protected Response post(String path, String jsonBody) {
        return exchange(HttpMethod.POST, path, jsonBody, null);
    }

    protected Response put(String path, String jsonBody) {
        return exchange(HttpMethod.PUT, path, jsonBody, null);
    }

    protected Response patch(String path, String jsonBody) {
        return exchange(HttpMethod.PATCH, path, jsonBody, null);
    }

    protected Response delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, null);
    }

    protected Response get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    protected Response post(String path, String jsonBody, String token) {
        return exchange(HttpMethod.POST, path, jsonBody, token);
    }

    protected Response put(String path, String jsonBody, String token) {
        return exchange(HttpMethod.PUT, path, jsonBody, token);
    }

    protected Response patch(String path, String jsonBody, String token) {
        return exchange(HttpMethod.PATCH, path, jsonBody, token);
    }

    protected Response delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    /**
     * Ejecuta la llamada y registra el intercambio en {@link FailureCapture}. Nunca
     * lanza por un 4xx/5xx: el status es una aserción del escenario, no un error.
     */
    protected Response exchange(HttpMethod method, String path, String jsonBody, String token) {
        return exchange(method, path, jsonBody, token, idempotencyKey());
    }

    /**
     * Variante con `Idempotency-Key` explícita: repetir la misma clave es lo que
     * ejercita la deduplicación. En el resto de escenarios va una uuid nueva por
     * request — reutilizarla entre flujos devuelve la respuesta del anterior
     * mientras dure el ttlSeconds del diseño.
     */
    protected Response exchangeWithKey(HttpMethod method, String path, String jsonBody, String token, String idempotencyKey) {
        return exchange(method, path, jsonBody, token, idempotencyKey);
    }

    /**
     * Variante SIN `Idempotency-Key`: el escenario «falta la cabecera», que el diseño
     * cierra con un code 400 cuando la exige (OBL-IDEM-KEY-REQUIRED). Ninguna otra llamada
     * del arnés puede omitirla —todas inyectan una uuid fresca, y pasar un mapa de cabeceras
     * vacío no la quita—, así que sin este método ese escenario no se puede escribir. Lo
     * añadió a mano el agente de pruebas en la corrida `catalog` (2026-09-21).
     */
    protected Response exchangeWithoutIdempotencyKey(HttpMethod method, String path, String jsonBody, String token) {
        return exchange(method, path, jsonBody, token, null);
    }

    private Response exchange(HttpMethod method, String path, String jsonBody, String token, String idempotencyKey) {
        return exchange(method, path, jsonBody, token, idempotencyKey, null);
    }

    private Response exchange(HttpMethod method, String path, String jsonBody, String token, String idempotencyKey,
            Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null && isMutation(method)) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        // Al final a propósito: el escenario añade lo suyo sobre lo que el arnés ya puso,
        // no al revés.
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        ResponseEntity<String> entity = rest.exchange(uriOf(path), method, new HttpEntity<>(jsonBody, headers), String.class);
        Response response = new Response(entity.getStatusCode().value(), entity.getHeaders(), entity.getBody());
        FailureCapture.record(method.name(), path, headers, jsonBody, response);
        return response;
    }

    /**
     * Resuelve el path a un {@link URI} <b>ya codificado</b>. Es la única forma de
     * llamar a {@code rest.exchange(...)} en este arnés.
     *
     * <p>Pasarle un {@code String} elige la sobrecarga de PLANTILLA de URI, que vuelve a
     * codificar lo que ya venía codificado: el {@code %40} de un email en la ruta llega
     * al servidor como {@code %2540}, la petición no autentica y el escenario ve un 401
     * que no tiene nada que ver con lo que estaba probando. Con {@code build(true)} se le
     * dice a Spring que el path ya está codificado y que no lo toque.
     *
     * <p>Efecto deliberado: si el escenario compone una URL con un carácter ilegal sin
     * codificar (un espacio crudo), esto <b>falla aquí</b>, señalando la URL. Antes esa
     * misma entrada se corrompía en silencio y el fallo aparecía como un status inesperado.
     */
    protected static URI uriOf(String path) {
        return UriComponentsBuilder.fromUriString(path).build(true).toUri();
    }

    /**
     * Los verbos que MUTAN, y por tanto los que llevan la {@code Idempotency-Key}.
     *
     * <p>DELETE está dentro, y su ausencia costó una corrida: el filtro de idempotencia del
     * servicio no discrimina por verbo, así que un {@code DELETE} con
     * {@code idempotency: client-key} está soportado por el servidor — pero el arnés le
     * quitaba la cabecera, y con ella se quedaban sin poder escribirse los DOS escenarios que
     * el mecanismo exige: el reintento deduplicado y el «falta la cabecera → error».
     * Vacuamente en verde, que es peor que en rojo.
     */
    private static boolean isMutation(HttpMethod method) {
        return HttpMethod.POST.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method)
                || HttpMethod.DELETE.equals(method);
    }


    protected String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    // ── Aserciones ───────────────────────────────────────────────────────────

    /**
     * Cuerpo completo en modo estricto: campos presentes <b>y</b> ausentes en una
     * sola aserción. Los valores no deterministas (ids, marcas de tiempo) no van
     * aquí: se extraen con {@link #jsonPath} y se verifican por forma.
     */
    protected void assertBody(Response response, String expectedJson) {
        assertJson(response.body(), expectedJson);
    }

    /**
     * Lo mismo, sobre un JSON que no viene de una {@link Response}: el cuerpo
     * <b>saliente</b> que el servidor mandó al proveedor de prueba
     * ({@code stubRequestBody(...)}), o el {@code data} de un evento leído del broker.
     */
    protected void assertJson(String actualJson, String expectedJson) {
        try {
            JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
        } catch (Exception e) {
            throw new AssertionError("El cuerpo no coincide con el esperado: " + e.getMessage(), e);
        }
    }

    /** Valor no determinista del cuerpo (id generado, marca de tiempo), por JsonPath. */
    protected <T> T jsonPath(Response response, String path) {
        rejectIndexAfterFilter(path);
        return JsonPath.read(response.body(), path);
    }

    /**
     * Un elemento de una colección del cuerpo, localizado por el valor de uno de sus
     * campos. <b>Esta es la vía</b>: filtrar y quedarse con el primero se hace aquí, en
     * Java, no encadenando un índice al JsonPath.
     *
     * <p>Devuelve vacío si no hay ningún elemento que case. Para preguntar por un campo
     * del elemento —«¿ya se apagó la espera?»— se encadena sobre el {@code Optional}, y
     * un campo nulo da vacío igual que un elemento ausente, que es justo lo que quiere
     * decir la pregunta:
     *
     * <pre>itemById(response, "$.items", "id", productId)
     *     .map(item -&gt; item.get("recordWithdrawalAwaitingSince"))
     *     .isPresent()</pre>
     *
     * <p>El {@code collectionPath} es explícito porque no siempre es el mismo: un listado
     * paginado lo trae bajo {@code $.items} y una salida de lista es el array raíz
     * ({@code $}).
     */
    protected Optional<Map<String, Object>> itemById(Response response, String collectionPath, String idField, Object idValue) {
        List<Map<String, Object>> items = JsonPath.read(response.body(), collectionPath);
        return items.stream().filter(item -> String.valueOf(idValue).equals(String.valueOf(item.get(idField)))).findFirst();
    }

    /**
     * Rechaza un JsonPath que indexa DESPUÉS de un filtro, que es el error que más caro
     * sale de esta clase porque no falla: miente.
     *
     * <p>{@code $.items[?(@.id=='x')].campo[0]} no devuelve el campo del primer elemento
     * que casa. Un filtro hace el path <i>indefinido</i>, y Jayway devuelve entonces
     * SIEMPRE una {@code JSONArray} —vacía si no hay nada, pero nunca {@code null} y
     * nunca {@code PathNotFoundException}—, valga lo que valga el campo de verdad. Un
     * {@code valor != null} sobre eso es constantemente cierto, así que un
     * {@code await(...)} construido encima expira aunque el servidor haya convergido hace
     * rato: el síntoma es un timeout que parece latencia del servidor, y manda a buscar el
     * defecto donde no está. En la corrida del 13/08/2026 costó un ciclo de arbitraje
     * completo y contaminó el diagnóstico del defecto que sí era real.
     *
     * <p>La forma correcta es {@link #itemById}, que filtra y toma el primero en Java.
     */
    private static void rejectIndexAfterFilter(String path) {
        int filter = path.indexOf("?(");
        if (filter >= 0 && INDEX_AFTER_FILTER.matcher(path.substring(filter)).find()) {
            throw new IllegalArgumentException(
                    "JsonPath con índice después de un filtro: " + path
                            + " — un filtro hace el path indefinido y Jayway devuelve siempre una lista (vacía), nunca el elemento,"
                            + " así que la comprobación que montes encima será constantemente cierta. Usa itemById(...) y quédate"
                            + " con el primero en Java.");
        }
    }

    private static final Pattern INDEX_AFTER_FILTER = Pattern.compile("\\[\\s*\\d+\\s*\\]");

    /**
     * JSON válido a partir de un nodo <b>objeto o array</b> extraído con
     * {@link #jsonPath} (o con {@code JsonPath.read} sobre cualquier otra cadena).
     *
     * <p>Con jackson-databind en el classpath —lo trae {@code spring-boot-starter-web}—
     * el proveedor por defecto de JsonPath materializa esos nodos como
     * {@code LinkedHashMap}/{@code List}, y {@code Object.toString()} sobre ellos da
     * sintaxis de Java ({@code {clave=valor}}), no JSON: volver a leer ese texto con
     * {@code JsonPath.read} lanza {@code PathNotFoundException} aunque el servidor cumpla
     * el contrato, y compararlo con {@code JSONAssert} falla por su propia técnica.
     * Re-serializar con Jackson es agnóstico de qué proveedor de JsonPath esté activo.
     *
     * <p>El caso donde más aparece es el {@code data} de un evento leído del broker
     * ({@code $.data} de la envoltura {@code {metadata, data}}).
     */
    protected String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new AssertionError("No se pudo serializar el fragmento JSON: " + e.getMessage(), e);
        }
    }

    protected void assertIsUuid(String value) {
        UUID.fromString(value);
    }

    protected void assertIsInstant(String value) {
        Instant.parse(value);
    }

    /**
     * Espera activa para efectos asíncronos (publicación de eventos, consumo de
     * suscripciones). Sin dependencias nuevas: sondeo con periodo fijo.
     */
    protected void await(Duration timeout, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Espera interrumpida", e);
            }
        }
        throw new AssertionError("La condición no se cumplió en " + timeout);
    }

    /**
     * El gemelo NEGATIVO de {@link #await}: comprueba que algo <b>sigue sin pasar</b> durante
     * toda una ventana. Para un {@code Then} que afirma una ausencia sobre un efecto
     * asíncrono —la reentrega que no debe producir un segundo efecto, el «exactamente uno»
     * tras recuperar el canal, las cinco cancelaciones que no deben volverse diez—.
     *
     * <p>Existe porque la alternativa natural es una <b>lectura seca</b> justo después de
     * provocar el estímulo, y esa lectura <b>sale verde siempre</b>: mide el instante en que
     * el efecto duplicado todavía no ha tenido tiempo de llegar, no que no vaya a llegar.
     * Un escenario así no puede fallar, que es la peor clase de escenario —ocupa una fila de
     * la matriz y no prueba nada—.
     *
     * <p>La ventana se dimensiona por el mecanismo que produciría el efecto, igual que las de
     * {@code await}: el backoff del reintento del listener, el tick del cron, el timeout del
     * cliente del broker. Corta en cuanto la condición deja de cumplirse, así que el caso
     * bueno paga la ventana entera y el malo falla enseguida.
     *
     * <pre>{@code
     * redeliver(evento);
     * holdsFor(Duration.ofSeconds(10), () -> adjustmentCountOf(orderId) == 1);
     * }</pre>
     */
    protected void holdsFor(Duration window, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(window);
        while (Instant.now().isBefore(deadline)) {
            if (!condition.getAsBoolean()) {
                throw new AssertionError("La condición dejó de cumplirse antes de " + window);
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Espera interrumpida", e);
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("La condición dejó de cumplirse antes de " + window);
        }
    }

    private boolean preconditionsDone;
    private AssertionError preconditionsFailure;

    /**
     * Precondición que depende del <b>comportamiento de la aplicación</b>: una espera de
     * correo, un barrido que tiene que haber pasado, una transición del lifecycle que
     * ninguna llamada provoca directamente. Se invoca desde {@code @BeforeEach}, y esa
     * ubicación no es estilo.
     *
     * <p>En {@code @BeforeAll} el mismo fallo es <b>inatribuible</b>: JUnit aborta el
     * contenedor de la clase con {@code initializationError}, ningún escenario llega a
     * ejecutarse y sus {@code FL-*} salen de la matriz como NO_EJERCITADO — una etiqueta
     * que dice «sin cobertura» cuando lo que hubo fue un rojo del servidor. La puntuación
     * lo lee entonces como arnés roto y devuelve la corrida al agente de pruebas, que no
     * puede leer {@code src/main/java} y por tanto no puede arreglarlo. Ese bucle costó
     * una corrida entera.
     *
     * <p>Desde aquí, en cambio, el fallo cae sobre <b>cada escenario</b> de la clase: la
     * matriz los marca FALLO, {@code FailureCapture} deja su volcado, y quien arbitra es
     * {@code keel-spring-validate}, que sí puede dictaminar {@code culprit: code}.
     *
     * <p>Corre una sola vez por clase (las clases de flujo son
     * {@code @TestInstance(PER_CLASS)}) y <b>memoriza también el fallo</b>: sin eso, una
     * espera de correo agotada se pagaría entera en cada escenario de la clase.
     *
     * <p>Lo determinista —{@code resetState()}, las llamadas HTTP que devuelven 2xx por
     * contrato— se queda en {@code @BeforeAll}. Aquí va solo lo que el servidor puede
     * incumplir.
     */
    protected final void awaitPreconditions(Runnable seed) {
        if (preconditionsFailure != null) {
            throw preconditionsFailure;
        }
        if (preconditionsDone) {
            return;
        }
        try {
            seed.run();
            preconditionsDone = true;
        } catch (AssertionError e) {
            preconditionsFailure = e;
            throw e;
        }
    }

    /**
     * Ejecuta las tareas A LA VEZ y devuelve sus resultados en el orden de envío.
     *
     * <p>Para los escenarios que fijan qué pasa cuando dos peticiones idénticas, dos
     * entregas del mismo mensaje o dos mutaciones sobre la misma entidad coinciden en
     * el tiempo. Una sola instancia basta para ejercitarlos: el servidor es multihilo,
     * y el árbitro de la carrera —la clave primaria, el lock de fila— es el mismo que
     * arbitraría entre réplicas.
     *
     * <p>Tres reglas, y las tres son lo que hace que el escenario pruebe algo:
     * <ul>
     *   <li>Todas las tareas arrancan del <b>mismo latch</b>. Sin él, «simultáneo»
     *       acaba siendo «una detrás de otra» —el coste de crear cada hilo basta para
     *       serializarlas— y el escenario pasa sin haber ejercitado ninguna carrera.</li>
     *   <li>Las excepciones <b>se relanzan</b>, no se tragan: un fallo silenciado en un
     *       hilo secundario deja el {@code Then} afirmando sobre un {@code When} que
     *       nunca ocurrió.</li>
     *   <li>El método <b>no asserta nada</b>: junta y devuelve, para que toda aserción
     *       siga ocurriendo en el hilo del test. Ahí es donde JUnit las recoge.</li>
     * </ul>
     *
     * <p>El {@code Then} de una carrera se escribe como disyunción cerrada (los
     * desenlaces admisibles, enumerados) más al menos una afirmación que no dependa de
     * quién ganó — normalmente un conteo leído por la API. Ver
     * conventions/integration-tests.md.
     *
     * <p><b>Lo que el latch NO garantiza, y por qué la disyunción cerrada no es un
     * formalismo.</b> El latch sincroniza la SALIDA de los hilos cliente; lo que pase
     * después es del planificador. Medido en la corrida {@code catalog} (2026-09): dos
     * peticiones salieron con ~74 µs de diferencia —concurrencia real— y aun así uno de
     * los dos hilos se comió una pausa de ~10 ms entre dos {@code SELECT} consecutivos,
     * sin lógica de negocio entre medias, con el daemon de Gradle recién arrancado
     * (JIT/GC/antivirus). Las mismas clases pasaron 27/27 en caliente. Un {@code Then}
     * que exija que la carrera se solape de verdad falla ahí, y el rojo no habla del
     * servicio. No se persigue con una barrera más fina: sincronizar las dos peticiones
     * DENTRO del servidor pide un gancho en el código de producción, y este arnés es de
     * caja negra —su source set ni siquiera compila contra {@code main}—. Lo que hace
     * que el escenario siga probando algo es el Then: enumerar los desenlaces admisibles
     * (incluido «se serializaron») y afirmar aparte lo que no depende de quién ganó.
     */
    protected <T> List<T> race(List<Callable<T>> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (Callable<T> task : tasks) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : pending) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    throw new AssertionError("Una rama de la carrera falló", e.getCause());
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("La carrera fue interrumpida", e);
        } catch (TimeoutException e) {
            throw new AssertionError("La carrera no terminó a tiempo", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /** La misma llamada, {@code times} veces a la vez. Ver {@link #race}. */
    protected <T> List<T> raceOf(int times, Callable<T> task) {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return race(tasks);
    }

    // ── Estado e infraestructura ─────────────────────────────────────────────

    /**
     * Deja el estado como recién arrancado. Se invoca desde el `@BeforeAll` de cada
     * clase de flujo: el reset es <b>por flujo</b>, nunca entre escenarios — dentro
     * de un flujo, un escenario usa lo que dejó el anterior.
     *
     * <p>Cubre exactamente lo que enumera `infra/reset-db.sh`: datos de la BD, claves
     * de la caché, destinos de mensajería declarados. Un recurso que no esté en esa
     * lista <b>no</b> se puede dar por limpio.
     */
    protected static void resetState() {
        restoreBroker();
        stopReplica();
        runInfraScript("infra/reset-db.sh", "¿Está la infraestructura arriba?");
    }

    /**
     * Ejecutable de bash con el que se invocan los scripts de `infra/`.
     *
     * <p>Dejar que {@code CreateProcess} resuelva {@code "bash"} por {@code PATH} es
     * ambiguo en Windows: {@code %SystemRoot%\System32} se consulta antes que el resto
     * del {@code PATH} y ahí vive el lanzador de WSL, un entorno Linux aislado que no ve
     * el {@code PATH} ni las variables de Windows — los scripts fallan aunque funcionen
     * desde Git Bash. Se resuelve explícitamente el bash de Git for Windows, con override
     * por {@code BASH_EXECUTABLE}, y se cae a {@code "bash"} literal fuera de Windows.
     */
    private static String bashExecutable() {
        String configured = System.getenv("BASH_EXECUTABLE");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String[] candidates = {
                System.getenv("ProgramFiles") + "\\Git\\bin\\bash.exe",
                System.getenv("ProgramFiles(x86)") + "\\Git\\bin\\bash.exe",
                System.getenv("LOCALAPPDATA") + "\\Programs\\Git\\bin\\bash.exe",
                "C:\\Program Files\\Git\\bin\\bash.exe"
            };
            for (String candidate : candidates) {
                if (candidate != null && new File(candidate).isFile()) {
                    return candidate;
                }
            }
        }
        return "bash";
    }

    /**
     * Ejecuta un script de {@code infra/} y <b>captura su salida</b>, que es la evidencia.
     *
     * <p><b>Por qué no {@code inheritIO()}</b>, que es lo que había aquí: esa salida va a los
     * descriptores del worker de Gradle, no a {@code System.out}, así que no la recoge ni
     * {@code build/keel-scenarios/run.log} ni el {@code system-out} del XML de JUnit. El fallo
     * llegaba como «falló (código 1)» y nada más — imposible de diagnosticar sin reproducirlo, que
     * es justo lo que la regla de {@code score-scenarios.sh} evita: la causa de un rojo tiene que
     * llegar por stdout.
     *
     * <p><b>El probe se registra SIEMPRE</b>, no solo al fallar, y esa es la otra mitad. Un
     * {@code @BeforeAll} que revienta deja {@code build/keel-failures/&lt;Clase&gt;-init.json}, y ese
     * volcado resuelve el sondeo cayendo a un campo estático compartido por toda la JVM: sin
     * registrar el reset, el volcado de esta clase podía mostrar el comando de OTRA, presentado
     * como «el último comando ejecutado». Evidencia ajena con aspecto de propia es peor que
     * ninguna.
     *
     * <p>El mensaje lleva el final de la salida y no toda: el error está al final, y el caso bueno
     * de {@code reset-db.sh} imprime una línea por canal purgado. La salida completa viaja en el
     * probe hasta el volcado.
     */
    private static void runInfraScript(String script, String hint) {
        List<String> command = List.of(bashExecutable(), script);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            FailureCapture.recordProbe(command, exit, output);
            if (exit != 0) {
                throw new IllegalStateException(
                        script + " falló (código " + exit + "). " + hint + System.lineSeparator()
                                + lastLines(output));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido ejecutando " + script, e);
        }
    }

    /** Las últimas líneas de una salida: donde está el error, sin arrastrar el resto. */
    private static String lastLines(String output) {
        if (output == null || output.isBlank()) {
            return "(el script no escribió nada)";
        }
        String[] lines = output.strip().split("\\R");
        int from = Math.max(0, lines.length - 12);
        return String.join(System.lineSeparator(), Arrays.copyOfRange(lines, from, lines.length));
    }

    /** Admin API del proveedor de prueba (WireMock de infra/docker-compose.yaml). */
    private static final String STUB_ADMIN = "http://localhost:8090/__admin";

    private static final HttpClient STUB_HTTP = HttpClient.newHttpClient();

    /**
     * Programa qué responde el proveedor en <b>este</b> escenario: método, ruta
     * (regex sobre el path, sin query) y la respuesta que verá el servidor.
     *
     * <p>Clientes que salen por aquí: la pasarela de pago. Sus `base-url` apuntan al stub en
     * el perfil `local`, que es el que activan estas pruebas.
     *
     * <p>El Given de cada flujo programa lo suyo y {@code resetState()} lo limpia
     * antes de la clase siguiente: un mapping que sobrevive a su escenario es
     * estado global y hace que el orden de ejecución decida el resultado.
     */
    protected static void stubFor(String method, String pathPattern, int status, String jsonBody) {
        stubMapping(method, pathPattern, stubOkBody(status, jsonBody), null, null, null);
    }

    /**
     * Fallo del proveedor sin cuerpo útil: lo que ejercita el fallback declarado
     * (onFailure/onMiss) y el circuit breaker. Un 5xx es reintentable; un 4xx no.
     */
    protected static void stubFailure(String method, String pathPattern, int status) {
        stubFor(method, pathPattern, status, "{}");
    }

    /**
     * El proveedor <b>no contesta</b>: corta la conexión antes de responder.
     *
     * <p>No es lo mismo que un 5xx y la diferencia importa, porque el diseño la
     * declara: una llamada con {@code retryOn: [timeout, connection]} —lo habitual en
     * una escritura ajena, donde repetir un 5xx puede duplicar el efecto— <b>no</b>
     * reintenta un 500 y sí reintenta esto. Sin esta primitiva, ningún escenario puede
     * ejercitar esa rama, y un retry declarado así queda sin cubrir por construcción.
     *
     * <p>Del lado del servidor llega como {@code ResourceAccessException}, que es lo
     * que el generador lista en {@code retry-exceptions} para {@code connection}.
     */
    protected static void stubConnectionFault(String method, String pathPattern) {
        stubMapping(method, pathPattern, stubFaultBody(), null, null, null);
    }

    /**
     * El proveedor tarda más de lo que la llamada tolera: el otro modo de fallo que
     * el DSL distingue del 5xx ({@code retryOn: [timeout]}).
     *
     * <p>{@code delayMs} tiene que superar el {@code timeoutMs} declarado para esa
     * llamada, o el escenario mide una respuesta lenta y no un timeout. Llega como
     * {@code ResourceAccessException}, igual que el corte de conexión.
     */
    protected static void stubTimeout(String method, String pathPattern, int delayMs) {
        stubMapping(method, pathPattern, stubSlowBody(delayMs), null, null, null);
    }

    /** Rutas con secuencia ya programada. {@link #resetStubs()} lo vacía entre clases. */
    private static final Set<String> SEQUENCED = new HashSet<>();

    /**
     * Una respuesta programada del proveedor, para {@link #stubSequence}.
     *
     * <p>Las cuatro formas son las mismas que ya tienen helper de un disparo y comparten con
     * ellos el renderizado. Escribirlas dos veces se separa a la primera que alguien cambie una
     * cabecera o el nombre del fault, y el síntoma sería un escenario que mide otra cosa.
     */
    protected record StubResponse(String json) {

        /** Respuesta normal, con su cuerpo. */
        public static StubResponse ok(int status, String jsonBody) {
            return new StubResponse(stubOkBody(status, jsonBody));
        }

        /** Fallo sin cuerpo útil: un 5xx es reintentable, un 4xx no. */
        public static StubResponse failure(int status) {
            return new StubResponse(stubOkBody(status, "{}"));
        }

        /**
         * No contesta a tiempo. {@code delayMs} tiene que superar el {@code timeoutMs} declarado
         * para esa llamada, o el escenario mide una respuesta lenta y no un timeout.
         */
        public static StubResponse timeout(int delayMs) {
            return new StubResponse(stubSlowBody(delayMs));
        }

        /** Corta la conexión antes de responder. */
        public static StubResponse connectionFault() {
            return new StubResponse(stubFaultBody());
        }
    }

    /**
     * Programa respuestas DISTINTAS para llamadas sucesivas a la misma ruta.
     *
     * <p>Es lo que hace escribible un escenario de REINTENTO: «falla la primera, responde bien la
     * segunda, y comprueba que la segunda petición repite la misma cabecera de idempotencia». Con
     * un solo mapping por ruta eso no se puede expresar —la respuesta es fija— y un escenario así
     * se queda sin traducir. Pasó: en la corrida refunds-rabbit el escenario del reintento contra
     * el libro mayor se reportó NO_EJERCITADO por esto, y con él la idempotencia SALIENTE se
     * quedó sin medir en toda la corrida.
     *
     * <p><b>La última respuesta se queda pegada.</b> Una tercera llamada vuelve a recibirla en vez
     * de no casar con nada: si el estado avanzara más allá del último mapping el proveedor
     * devolvería 404, y el escenario fallaría por el stub en vez de por lo que mide — que es
     * indistinguible de un defecto del servicio hasta que alguien lee el log de WireMock.
     *
     * <p>Una ruta admite UNA secuencia por escenario. Programarla dos veces encadenaría desde un
     * estado que la primera ya dejó atrás, así que se falla en el sitio y con el nombre delante.
     *
     * <pre>{@code
     * stubSequence("POST", "/entries",
     *         StubResponse.timeout(3000),
     *         StubResponse.ok(201, "{}"));
     * // …la acción que dispara la llamada…
     * List<String> peticiones = stubRequests("POST", "/entries");
     * assertEquals(2, peticiones.size());
     * assertEquals(stubRequestHeader(peticiones.get(0), "Idempotency-Key"),
     *              stubRequestHeader(peticiones.get(1), "Idempotency-Key"));
     * }</pre>
     */
    protected static void stubSequence(String method, String pathPattern, StubResponse... responses) {
        if (responses.length < 2) {
            throw new IllegalArgumentException(
                    "stubSequence con menos de dos respuestas no es una secuencia: para una sola usa"
                            + " stubFor, stubFailure, stubTimeout o stubConnectionFault");
        }
        String key = method + " " + pathPattern;
        if (!SEQUENCED.add(key)) {
            throw new IllegalStateException(
                    "ya hay una secuencia programada para " + key + " en este escenario: la segunda"
                            + " encadenaría desde un estado que la primera dejó atrás. Programa una sola.");
        }
        String scenario = "seq-" + Integer.toHexString(key.hashCode());
        String state = "Started";
        for (int i = 0; i < responses.length; i++) {
            String next = i == responses.length - 1 ? null : scenario + "-" + (i + 1);
            stubMapping(method, pathPattern, responses[i].json(), scenario, state, next);
            state = next;
        }
    }

    /** El cuerpo de una respuesta normal del stub. Fuente única de su forma. */
    private static String stubOkBody(int status, String jsonBody) {
        return """
                {"status": %d, "headers": {"Content-Type": "application/json"}, "body": %s}"""
                .formatted(status, jsonBody == null ? "\"\"" : quote(jsonBody));
    }

    /** El cuerpo de una respuesta que tarda más de lo tolerado. */
    private static String stubSlowBody(int delayMs) {
        return """
                {"status": 200, "fixedDelayMilliseconds": %d,
                 "headers": {"Content-Type": "application/json"}, "body": "{}"}"""
                .formatted(delayMs);
    }

    /** El cuerpo de un corte de conexión, que no es una respuesta HTTP. */
    private static String stubFaultBody() {
        return """
                {"fault": "CONNECTION_RESET_BY_PEER"}""";
    }

    /**
     * Registra un mapping en el proveedor. Con {@code scenario} no nulo el mapping solo responde
     * en ese estado de la máquina y deja el siguiente, que es lo que encadena una secuencia.
     *
     * <p>El JSON se compone con {@link #quote}, no con comillas a mano: el patrón de ruta es una
     * REGEX y puede traer barras o comillas, que interpoladas en crudo rompen el mapping — y el
     * stub lo rechaza con un 400 que no dice de qué escenario venía.
     */
    private static void stubMapping(String method, String pathPattern, String responseJson,
            String scenario, String requiredState, String nextState) {
        StringBuilder mapping = new StringBuilder("{");
        if (scenario != null) {
            mapping.append(quote("scenarioName")).append(": ").append(quote(scenario)).append(", ")
                    .append(quote("requiredScenarioState")).append(": ").append(quote(requiredState)).append(", ");
            if (nextState != null) {
                mapping.append(quote("newScenarioState")).append(": ").append(quote(nextState)).append(", ");
            }
        }
        mapping.append(quote("request")).append(": {")
                .append(quote("method")).append(": ").append(quote(method)).append(", ")
                .append(quote("urlPathPattern")).append(": ").append(quote(pathPattern)).append("}, ")
                .append(quote("response")).append(": ").append(responseJson).append("}");
        stubAdmin("/mappings", mapping.toString());
    }


    /**
     * Cuántas veces llamó el servidor al proveedor. Es la única forma de afirmar
     * en caja negra que un dato se pidió una vez y se cacheó, o que una activación
     * con {@code onFailure: ignore} no se reintentó.
     */
    protected static int stubCallCount(String method, String pathPattern) {
        return JsonPath.read(stubAdmin("/requests/count", stubCriterion(method, pathPattern)), "$.count");
    }

    /**
     * Las peticiones que recibió el proveedor, cada una como el JSON con que las
     * registra el stub: {@code {"url":…, "method":…, "headers":{…}, "body":"…"}}.
     *
     * <p>Es el Then que no se conforma con <i>cuántas</i> veces se llamó al proveedor
     * sino con <b>qué</b> se le envió: que el cuerpo saliente lleve los campos que el
     * diseño promete, o que la llamada viajara con la cabecera de idempotencia — sin
     * ella, un reintento nuestro encarga dos veces el mismo trabajo y eso no se ve
     * desde fuera. Con {@link #stubRequestBody} y {@link #stubRequestHeader} se leen
     * las dos piezas sin conocer el formato del stub.
     */
    protected static List<String> stubRequests(String method, String pathPattern) {
        List<Object> found = JsonPath.read(stubAdmin("/requests/find", stubCriterion(method, pathPattern)), "$.requests");
        return found.stream().map(AbstractFlowIT::serialize).toList();
    }

    /** El cuerpo que viajó en esa petición, listo para {@link #assertJson}. */
    protected static String stubRequestBody(String requestJson) {
        return JsonPath.read(requestJson, "$.body");
    }

    /**
     * Una cabecera de esa petición, buscada <b>sin distinguir mayúsculas</b>: quien
     * decide el caso del nombre es el cliente HTTP, no el contrato. {@code null} si
     * la petición no la llevaba.
     */
    protected static String stubRequestHeader(String requestJson, String name) {
        Map<String, Object> headers = JsonPath.read(requestJson, "$.headers");
        for (Map.Entry<String, Object> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(name)) {
                return String.valueOf(header.getValue());
            }
        }
        return null;
    }

    /** Borra los mappings, el log de peticiones y las secuencias. Lo llama {@link #resetState()}. */
    protected static void resetStubs() {
        // El registro de secuencias va con los mappings: si sobreviviera al reset, la clase
        // siguiente no podría programar la misma ruta y fallaría por el guard, no por su escenario.
        SEQUENCED.clear();
        stubAdmin("/reset", "");
    }

    /**
     * Criterio de búsqueda del admin API, compartido por el conteo y el log: los dos
     * tienen que seleccionar exactamente las mismas peticiones, así que es un literal.
     */
    private static String stubCriterion(String method, String pathPattern) {
        return """
                {"method": "%s", "urlPathPattern": "%s"}""".formatted(method, pathPattern);
    }

    private static String stubAdmin(String path, String body) {
        try {
            HttpResponse<String> response = STUB_HTTP.send(
                    HttpRequest.newBuilder(URI.create(STUB_ADMIN + path))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new AssertionError(
                        "El proveedor de prueba rechazó " + path + " (HTTP " + response.statusCode() + "): " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError(
                    "No se pudo hablar con el proveedor de prueba en " + STUB_ADMIN
                            + ". ¿Está levantado el compose de infra/? (bash infra/validate-infra.sh)", e);
        }
    }

    /** Escapa un JSON para incrustarlo como cadena dentro de otro JSON. */
    private static String quote(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new AssertionError("No se pudo escapar el cuerpo del stub", e);
        }
    }

    /**
     * Vuelve a JSON un nodo que extrajo JsonPath. Mismo motivo que {@link #toJson}:
     * {@code toString()} sobre el {@code LinkedHashMap} que materializa JsonPath da
     * sintaxis de Java, no JSON, y volver a leerlo lanza {@code PathNotFoundException}.
     */
    private static String serialize(Object node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            throw new AssertionError("No se pudo serializar la petición del stub", e);
        }
    }

    // ── Pasarela de pago de prueba (capa payments, Stripe (Payment Intents)) ─────────────
    //
    // La API es NEUTRA: los mismos métodos con cualquier pasarela, porque los escenarios no nombran
    // ninguna. Cada método programa el WireMock de infra/ con la forma de la elegida.

    /** Las llamadas que el servidor le hace a la pasarela. */
    protected enum GatewayCall { CHARGE, CAPTURE, CANCEL, REFUND, STATUS, SEARCH, SAVE_METHOD, ATTACH_METHOD }

    private static final Map<GatewayCall, String[]> GATEWAY_ROUTES = Map.ofEntries(
            Map.entry(GatewayCall.CHARGE, new String[] {"POST", "/v1/payment_intents"}),
            Map.entry(GatewayCall.CAPTURE, new String[] {"POST", "/v1/payment_intents/[^/]+/capture"}),
            Map.entry(GatewayCall.CANCEL, new String[] {"POST", "/v1/payment_intents/[^/]+/cancel"}),
            Map.entry(GatewayCall.REFUND, new String[] {"POST", "/v1/refunds"}),
            Map.entry(GatewayCall.STATUS, new String[] {"GET", "/v1/payment_intents/[^/]+"}),
            Map.entry(GatewayCall.SEARCH, new String[] {"GET", "/v1/payment_intents/search"}),
            Map.entry(GatewayCall.SAVE_METHOD, new String[] {"POST", "/v1/customers"}),
            Map.entry(GatewayCall.ATTACH_METHOD, new String[] {"POST", "/v1/payment_methods/[^/]+/attach"}));

    /** El secreto con el que el perfil local verifica los avisos (parameters/local/payments.yaml). */
    private static final String GATEWAY_WEBHOOK_SECRET = "keel-test-webhook-secret";

    /** La moneda de los escenarios (testValue del parámetro de despliegue). */
    private static final String GATEWAY_CURRENCY = "EUR";

    /**
     * El id que la pasarela de prueba asigna al cobro de esa referencia. Es determinista para que el
     * escenario pueda nombrarlo antes de pedir el cobro (para un aviso, por ejemplo).
     */
    protected static String gatewayIdFor(String reference) {
        return "pi_" + reference.replaceAll("[^A-Za-z0-9]", "");
    }

    /** El siguiente cobro con esa referencia queda autorizado (importe retenido). */
    protected static void gatewayAuthorizes(String reference) {
        gatewayStub(GatewayCall.CHARGE, null, reference, 200, gatewayObject(reference, "AUTHORIZED", null, null));
    }

    /** El siguiente cobro con esa referencia queda cobrado en el acto. */
    protected static void gatewayCharges(String reference) {
        gatewayStub(GatewayCall.CHARGE, null, reference, 200, gatewayObject(reference, "CAPTURED", null, null));
    }

    /** El siguiente cobro con esa referencia exige que el cliente se autentique (3DS). */
    protected static void gatewayRequiresAction(String reference) {
        gatewayStub(GatewayCall.CHARGE, null, reference, 200, gatewayObject(reference, "ACTION_REQUIRED", null, null));
    }

    /**
     * El siguiente cobro con esa referencia se rechaza, con un motivo del vocabulario neutro
     * (declined, insufficientFunds, expiredCard, authenticationFailed, fraudSuspected,
     * invalidPaymentMethod, processingError).
     */
    protected static void gatewayDeclines(String reference, String reason) {
        gatewayDecline(reference, reason);
    }

    /** La pasarela no contesta a esa llamada: corta la conexión. La acción queda EN DUDA. */
    protected static void gatewayDoesNotAnswer(GatewayCall call) {
        String[] route = GATEWAY_ROUTES.get(call);
        stubConnectionFault(route[0], route[1]);
    }

    /**
     * Lo que responde la pasarela cuando se le pregunta por el cobro de esa referencia, por su id y
     * por la referencia. Con "NOT_FOUND" no lo conoce: la petición no le llegó nunca.
     */
    protected static void gatewayReports(String reference, String status) {
        if ("NOT_FOUND".equals(status)) {
            gatewayStubPath(GatewayCall.STATUS, gatewayIdFor(reference), null, 404, "{}");
            gatewayStub(GatewayCall.SEARCH, reference, null, 200, "{\"data\": []}");
            return;
        }
        String object = gatewayObject(reference, status, null, "REFUNDED".equals(status) ? new BigDecimal("10.00") : null);
        gatewayStubPath(GatewayCall.STATUS, gatewayIdFor(reference), null, 200, object);
        gatewayStub(GatewayCall.SEARCH, reference, null, 200, "{\"data\": [" + object + "]}");
    }

    /** La pasarela captura el cobro de esa referencia en el acto. */
    protected static void gatewayCaptures(String reference) {
        gatewayStubPath(GatewayCall.CAPTURE, gatewayIdFor(reference), null, 200, gatewayObject(reference, "CAPTURED", null, null));
    }

    /** La pasarela anula la autorización del cobro de esa referencia en el acto. */
    protected static void gatewayCancels(String reference) {
        gatewayStubPath(GatewayCall.CANCEL, gatewayIdFor(reference), null, 200, gatewayObject(reference, "CANCELED", null, null));
    }

    /**
     * La autorización del cobro de esa referencia caducó antes de capturarse: la captura se
     * rechaza y, preguntada, la pasarela lo da por anulado. Es el desenlace que la pasarela
     * impone sola pasado su plazo (unos días), y ningún escenario puede esperarlo.
     */
    protected static void gatewayExpiresAuthorization(String reference) {
        gatewayStubPath(GatewayCall.CAPTURE, gatewayIdFor(reference), null, 400, gatewayExpiredCapture(reference));
        gatewayReports(reference, "CANCELED");
    }

    /**
     * La pasarela rechaza esa llamada (4xx): la acción no se hizo. Tras rechazar una captura, una anulación o una
     * devolución, el servidor le PREGUNTA a la pasarela el estado real del cobro (el rechazo no lo dice): programa también
     * {@code gatewayReports(referencia, "AUTHORIZED")} —o "CAPTURED" para una devolución—. Sin él la consulta no lo
     * encuentra (NOT_FOUND), y un rechazo no se distingue de un cobro que la pasarela no conoce.
     */
    protected static void gatewayRejects(GatewayCall call) {
        String[] route = GATEWAY_ROUTES.get(call);
        stubFor(route[0], route[1], 400, "{\"error\": {\"code\": \"rejected_by_test\"}}");
    }

    /** La pasarela guarda el medio de pago que se le pase. */
    protected static void gatewaySavesPaymentMethod() {
        String[] save = GATEWAY_ROUTES.get(GatewayCall.SAVE_METHOD);
        String[] attach = GATEWAY_ROUTES.get(GatewayCall.ATTACH_METHOD);
        stubFor(save[0], save[1], 200, "{\"id\": \"cus_keel_test\"}");
        stubFor(attach[0], attach[1], 200, "{\"id\": \"card_keel_test\"}");
    }

    /** Cuántas veces llamó el servidor a la pasarela por esa llamada. */
    protected static int gatewayCallCount(GatewayCall call) {
        String[] route = GATEWAY_ROUTES.get(call);
        return stubCallCount(route[0], route[1]);
    }

    /** Las peticiones que recibió la pasarela por esa llamada (para afirmar cabeceras y cuerpos). */
    protected static List<String> gatewayRequests(GatewayCall call) {
        String[] route = GATEWAY_ROUTES.get(call);
        return stubRequests(route[0], route[1]);
    }

    /** La pasarela avisa de que algo cambió en el cobro de esa referencia, firmado como lo firma ella. */
    protected Response sendGatewayNotice(String reference) {
        return gatewayNotice(reference, GATEWAY_WEBHOOK_SECRET);
    }

    /** Un aviso con la firma ALTERADA: el servidor tiene que rechazarlo sin consultar nada. */
    protected Response sendForgedGatewayNotice(String reference) {
        return gatewayNotice(reference, GATEWAY_WEBHOOK_SECRET + "-falso");
    }

    // Un mapping del doble para una llamada: con `bodyContains` solo casa la petición cuyo cuerpo lo
    // lleve (la referencia), con `queryContains` la que lo lleve en su parámetro de búsqueda.
    private static void gatewayStub(GatewayCall call, String queryContains, String bodyContains, int status, String json) {
        String[] route = GATEWAY_ROUTES.get(call);
        gatewayMapping(route[0], route[1], queryContains, bodyContains, status, json);
    }

    // Igual, sobre la ruta concreta de un id (la de captura, anulación o consulta de ESE cobro).
    private static void gatewayStubPath(GatewayCall call, String gatewayId, String bodyContains, int status, String json) {
        String[] route = GATEWAY_ROUTES.get(call);
        gatewayMapping(route[0], route[1].replace("(?!search)[^/]+", gatewayId).replace("[^/]+", gatewayId), null,
                bodyContains, status, json);
    }

    private static void gatewayMapping(String method, String pathPattern, String queryContains, String bodyContains,
            int status, String json) {
        StringBuilder request = new StringBuilder("{")
                .append(quote("method")).append(": ").append(quote(method)).append(", ")
                .append(quote("urlPathPattern")).append(": ").append(quote(pathPattern));
        if (queryContains != null) {
            request.append(", ").append(quote("queryParameters")).append(": {")
                    .append(quote("query")).append(": {").append(quote("contains")).append(": ")
                    .append(quote(queryContains)).append("}}");
        }
        if (bodyContains != null) {
            request.append(", ").append(quote("bodyPatterns")).append(": [{").append(quote("contains")).append(": ")
                    .append(quote(bodyContains)).append("}]");
        }
        request.append("}");
        String response = "{" + quote("status") + ": " + status + ", " + quote("headers") + ": {"
                + quote("Content-Type") + ": " + quote("application/json") + "}, " + quote("body") + ": " + quote(json) + "}";
        stubAdmin("/mappings", "{" + quote("request") + ": " + request + ", " + quote("response") + ": " + response + "}");
    }

    private static String gatewayHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final Map<String, String> GATEWAY_STATUSES = Map.ofEntries(
            Map.entry("PENDING", "processing"),
            Map.entry("ACTION_REQUIRED", "requires_action"),
            Map.entry("AUTHORIZED", "requires_capture"),
            Map.entry("CAPTURED", "succeeded"),
            Map.entry("CANCELED", "canceled"),
            Map.entry("FAILED", "requires_payment_method"),
            Map.entry("REFUNDED", "succeeded"));

    private static final Map<String, String> GATEWAY_DECLINES = Map.ofEntries(
            Map.entry("declined", "generic_decline"),
            Map.entry("insufficientFunds", "insufficient_funds"),
            Map.entry("expiredCard", "expired_card"),
            Map.entry("fraudSuspected", "fraudulent"),
            Map.entry("authenticationFailed", "authentication_required"),
            Map.entry("invalidPaymentMethod", "incorrect_number"),
            Map.entry("notReceived", "generic_decline"),
            Map.entry("processingError", "processing_error"));

    // Un PaymentIntent con la forma que lee StripePaymentGateway.outcomeOf.
    private static String gatewayObject(String reference, String status, String declineCode, BigDecimal refunded) {
        String id = gatewayIdFor(reference);
        StringBuilder json = new StringBuilder("{\"id\": \"" + id + "\", \"object\": \"payment_intent\"")
                .append(", \"currency\": \"").append(GATEWAY_CURRENCY.toLowerCase(Locale.ROOT)).append("\"")
                .append(", \"status\": \"").append(GATEWAY_STATUSES.get(status)).append("\"")
                .append(", \"metadata\": {\"keel_reference\": \"").append(reference).append("\"}")
                .append(", \"client_secret\": \"").append(id).append("_secret_test\"");
        if ("ACTION_REQUIRED".equals(status)) {
            json.append(", \"next_action\": {\"type\": \"use_stripe_sdk\"}");
        }
        if ("FAILED".equals(status)) {
            json.append(", \"last_payment_error\": {\"code\": \"card_declined\", \"decline_code\": \"")
                    .append(declineCode == null ? "generic_decline" : declineCode).append("\"}");
        }
        if (refunded != null) {
            json.append(", \"latest_charge\": {\"amount_refunded\": ").append(refunded.movePointRight(2).longValueExact())
                    .append(", \"currency\": \"").append(GATEWAY_CURRENCY.toLowerCase(Locale.ROOT)).append("\"}");
        }
        return json.append("}").toString();
    }

    // Stripe rechaza un cobro confirmado con un 402 y el PaymentIntent dentro del error. El de
    // autenticación no: un 402 authentication_required es una ACCIÓN del cliente pendiente, así que
    // ese motivo llega como el PaymentIntent fallido que devuelve la consulta.
    private static void gatewayDecline(String reference, String reason) {
        String code = GATEWAY_DECLINES.getOrDefault(reason, "generic_decline");
        String failed = gatewayObject(reference, "FAILED", code, null);
        if ("authenticationFailed".equals(reason)) {
            gatewayStub(GatewayCall.CHARGE, null, reference, 200, failed);
            return;
        }
        gatewayStub(GatewayCall.CHARGE, null, reference, 402,
                "{\"error\": {\"type\": \"card_error\", \"code\": \"card_declined\", \"decline_code\": \"" + code
                        + "\", \"payment_intent\": " + failed + "}}");
    }

    // Stripe contesta a capturar una autorización caducada con este código, y el adaptador lo lee.
    private static String gatewayExpiredCapture(String reference) {
        return "{\"error\": {\"type\": \"invalid_request_error\", \"code\": \"charge_expired_for_capture\"}}";
    }

    /** La devolución del cobro de esa referencia se completa por ese importe. */
    protected static void gatewayRefunds(String reference, BigDecimal amount) {
        gatewayStub(GatewayCall.REFUND, null, gatewayIdFor(reference), 200,
                "{\"id\": \"re_" + gatewayIdFor(reference) + "\", \"status\": \"succeeded\", \"amount\": "
                        + amount.movePointRight(2).longValueExact() + ", \"currency\": \""
                        + GATEWAY_CURRENCY.toLowerCase(Locale.ROOT) + "\", \"payment_intent\": \"" + gatewayIdFor(reference) + "\"}");
    }

    private Response gatewayNotice(String reference, String secret) {
        String id = gatewayIdFor(reference);
        String body = "{\"id\": \"evt_" + id + "\", \"type\": \"payment_intent.updated\", \"data\": {\"object\": {\"id\": \""
                + id + "\", \"object\": \"payment_intent\"}}}";
        long t = Instant.now().getEpochSecond();
        return exchange(HttpMethod.POST, "/webhooks/payments", body, null, null,
                Map.of("Stripe-Signature", "t=" + t + ",v1=" + gatewayHmac(secret, t + "." + body)));
    }

    private static final String DEVTOOLS_CONTAINER = "payment-devtools";

    /**
     * Prefijo de los archivos del contenedor por los que viaja el cuerpo de {@link #deliverMessage}: uno por llamada
     * ({@link #devtoolsFile}), porque dos entregas a la vez sobre un archivo fijo se pisan el cuerpo.
     */
    private static final String DELIVER_BODY = "/tmp/keel-deliver";

    /**
     * Sentencias SQL preparadas desde que arrancó la aplicación.
     *
     * <p>Sirve para acotar el coste de una lectura: se lee antes y después de la
     * petición y se afirma sobre el SALTO, nunca sobre el valor absoluto (la cuenta es
     * del proceso entero y la comparten todas las clases de la suite).
     *
     * <pre>
     * long antes = queryCount();
     * Response response = get(unaRutaDeListado, admin);
     * assertThat(queryCount() - antes).isLessThanOrEqualTo(4);
     * </pre>
     *
     * <p><b>La cota se elige por FORMA, no por el número que salga hoy</b>: lo que se
     * está afirmando es que el coste NO crece con el tamaño de la página. Un margen
     * generoso sobre un coste constante sigue cazando el N+1; una cota ajustada al
     * valor exacto se rompe con cualquier índice o versión de Hibernate y acaba
     * subiéndose sin mirar, que es como un gate se convierte en ruido.
     *
     * <p>Para que la comparación signifique algo, la petición medida tiene que ser la
     * ÚNICA que corra en ese intervalo, y conviene ejecutarla dos veces midiendo la
     * segunda: la primera paga la caché de planes y la de segundo nivel.
     */
    // De INSTANCIA, no estático: se apoya en los helpers de petición, que lo son (y con
    // seguridad, en el que cachea el token por rol en un campo). Declararlo static —como
    // los helpers del broker, que sí lo son— no compila.
    protected long queryCount() {
        Response response = get("/actuator/metrics/hibernate.statements?tag=status:prepared", serviceCredential("checkout"));
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "No se pudo leer hibernate.statements (" + response.status() + "): ¿está el actuator expuesto y"
                            + " generate_statistics activo en el perfil?");
        }
        Number value = JsonPath.read(response.body(), "$.measurements[0].value");
        return value.longValue();
    }

    /**
     * Consultas (JPQL, criteria y nativas) ejecutadas desde que arrancó la aplicación.
     *
     * <p>El hermano de {@link #queryCount()} para cuando el When <b>escribe</b>: allí cuentan
     * también los INSERT/UPDATE —una colección de 20 elementos son 20 INSERT—, así que «el coste
     * de comprobar X no crece con N» sale rojo aunque la comprobación sea una sola consulta por
     * lote. Aquí solo cuentan las consultas, que es lo que el Then mide.
     *
     * <p><b>Su punto ciego</b>, y por eso no sustituye al otro: no cuenta las cargas por id
     * ({@code findById}, {@code em.find}) ni las inicializaciones perezosas de colecciones. Un
     * N+1 hecho a base de {@code findById} en un bucle NO se ve aquí; en una lectura pura, usa
     * {@link #queryCount()}.
     */
    protected long queryExecutions() {
        Response response = get("/actuator/metrics/hibernate.query.executions", serviceCredential("checkout"));
        if (response.status() != 200) {
            throw new IllegalStateException(
                    "No se pudo leer hibernate.query.executions (" + response.status() + "): ¿está el actuator expuesto y"
                            + " generate_statistics activo en el perfil?");
        }
        Number value = JsonPath.read(response.body(), "$.measurements[0].value");
        return value.longValue();
    }

    private static final String RABBIT_API = "http://rabbitmq:15672/api/queues/%2F/";

    /** Prefijo de los archivos del cuerpo del sondeo: uno por llamada, como en {@link #deliverMessage}. */
    private static final String PROBE_BODY = "/tmp/keel-probe";

    /**
     * Canal del diseño → destino físico. En este diseño coinciden todos, así que resolver es la
     * identidad; el punto de resolución existe igual para que los helpers no tengan que saberlo.
     */
    protected static String physicalDestination(String destination) {
        return destination;
    }

    /**
     * Últimos mensajes publicados en un destino, leídos del broker <b>real</b> del
     * compose (RabbitMQ) vía el contenedor devtools. Nunca un broker embebido:
     * lo que se valida es la infraestructura levantada.
     */
    protected static String publishedMessages(String destination, int count) {
        // Antes de leer: el relay corre en su propio fixed-delay, independiente del commit
        // del estado. Cuando un Then ve el estado ya cambiado, el evento puede no haber
        // salido todavía — y eso solo se nota bajo la contención de la suite completa, no
        // corriendo la clase sola.
        awaitOutboxDrained(destination);
        // Peek (ack_requeue_true): leer no consume, así que un escenario puede
        // assertar dos veces sobre el mismo mensaje.
        String probeFile = devtoolsFile(PROBE_BODY);
        try {
            copyToDevtools("{\"count\":" + count + ",\"ackmode\":\"ack_requeue_true\",\"encoding\":\"auto\"}", probeFile);
            // Desescapado ANTES de devolver: quien llama nunca ve el sobre de
            // aplicación como cadena escapada, que es la trampa que hacía fallar en
            // silencio aserciones por lo demás correctas.
            return decodePayloads(devtools("curl", "-sf", "-u", "guest:guest", "-H", "content-type: application/json", "-XPOST", "-d", "@" + probeFile, RABBIT_API + physicalDestination(destination) + "/get"));
        } catch (RuntimeException e) {
            return emptyIfBrokerStopped(e);
        } finally {
            removeFromDevtools(probeFile);
        }
    }

    /**
     * Captura el valor entrecomillado de cada campo {@code "payload": "..."} de la
     * respuesta de la Management API.
     *
     * <p>El grupo repetido es <b>posesivo</b> ({@code *+}) por la misma razón que su
     * gemelo de SQS: con un payload lleno de backslashes consecutivos, la forma perezosa
     * es ambigua sobre cómo agrupar la racha y el motor prueba exponencialmente muchas
     * particiones antes de fallar — un solo mensaje así basta para agotar el stack. La
     * expansión es determinista de todos modos, así que la forma posesiva no cambia qué
     * matchea: solo le prohíbe al motor reconsiderarlo.
     */
    private static final Pattern PAYLOAD_FIELD = Pattern.compile("\"payload\":\\s*\"((?:\\\\.|[^\"\\\\])*+)\"");

    /**
     * Desescapa el {@code payload} de cada mensaje y lo deja EMBEBIDO como JSON.
     *
     * <p>Sin esto, lo que devuelve {@link #publishedMessages} trae el sobre de aplicación
     * escapado dentro del JSON de la respuesta, y una aserción de texto como
     * {@code .contains("\"status\":\"active\"")} no casa aunque el evento sea correcto —
     * y el fallo es mudo. Con esto, el resultado sigue siendo JSON válido: lo natural es
     * leerlo con {@code JsonPath} sobre {@code $[*].payload.metadata.eventType}.
     *
     * <p>Se toca <b>solo</b> el valor de {@code payload}: la envoltura
     * ({@code properties}, {@code routing_key}, {@code payload_bytes}…) queda intacta
     * porque hay comprobaciones que dependen de ella. Y sin mensajes no hay campo que
     * tocar, así que un destino vacío sigue leyéndose como {@code []}.
     */
    private static String decodePayloads(String raw) {
        Matcher matcher = PAYLOAD_FIELD.matcher(raw);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(raw, last, matcher.start());
            result.append("\"payload\": ").append(embeddedPayload(matcher.group(1)));
            last = matcher.end();
        }
        result.append(raw, last, raw.length());
        return result.toString();
    }

    /** El payload como valor JSON si lo es; si no, tal cual vino (entrecomillado y escapado). */
    private static String embeddedPayload(String escaped) {
        try {
            String decoded = JSON.readValue("\"" + escaped + "\"", String.class);
            JSON.readTree(decoded);
            return decoded;
        } catch (Exception notJson) {
            return "\"" + escaped + "\"";
        }
    }

    /**
     * Vacía el destino. El reset de estado lo hace por cada canal declarado al abrir
     * el flujo; el test lo repite <b>inmediatamente antes</b> de la acción cuyo Then
     * afirma que no se publica nada — si no, lo que se lee es el evento que publicó
     * la preparación del propio escenario.
     */
    private static void purgeDestination(String destination) {
        devtools("curl", "-sf", "-u", "guest:guest", "-XDELETE", RABBIT_API + physicalDestination(destination) + "/contents");
    }

    /**
     * Vacía el destino, esperando antes a que el outbox no tenga nada pendiente para él.
     *
     * <p>La espera no es prudencia: sin ella, un evento legítimo del escenario ANTERIOR
     * puede aterrizar en la cola justo después de la purga, y el escenario siguiente lo
     * lee como propio — con lo que falla una aserción de «no se publicó nada» por algo
     * que no tiene que ver con ella. El relay drena al confirmarse cada evento y su fase
     * respecto a este método es arbitraria.
     */
    protected static void purgeMessages(String destination) {
        awaitOutboxDrained(destination);
        purgeDestination(destination);
    }

    /**
     * Traduce a «canal vacío» el fallo de leer un destino <b>mientras el propio
     * escenario tiene el broker parado</b>, y solo ese. La condición es el flag, no el
     * tipo de error: una infraestructura que se cae por su cuenta sigue reventando la
     * suite en el punto donde se cayó.
     *
     * <p>Y «vacío» se dice como lo diga RabbitMQ, no con la cadena vacía. Cada broker
     * tiene su forma y el predicado que la reconoce es el mismo que usa el resto del arnés
     * (lib/broker-probes.js): devolver {@code ""} donde este espera {@code "[]"} hacía fallar
     * la aserción de canal vacío justo en el único escenario para el que esta palanca existe
     * —el broker caído y el outbox reteniendo—.
     */
    private static String emptyIfBrokerStopped(RuntimeException e) {
        if (brokerIntentionallyStopped()) {
            return "[]";
        }
        throw e;
    }

    /** ¿Está el broker parado porque lo paró {@link #stopBroker}? */
    private static boolean brokerIntentionallyStopped() {
        return BROKER_STOPPED.get();
    }

    /** Cuánto se espera, como mucho, a que el relay entregue lo que tenga pendiente. */
    private static final Duration OUTBOX_DRAIN_TIMEOUT = Duration.ofSeconds(15);

    /**
     * El destino FÍSICO que el publicador escribe en {@code outbox_event.destination}, que es
     * {@code messaging.publishing.destination} y NO el canal lógico del diseño. Se declara aquí
     * y no se reutiliza {@code EVENT_TOPIC} porque aquella constante solo existe en la rama
     * Kafka del arnés, y esta espera se emite con los tres brokers.
     */
    private static final String OUTBOX_DESTINATION =
            System.getenv().getOrDefault("MESSAGING_DESTINATION", "payment.events");

    /** Canales que ESTE servicio publica: son los únicos cuyo trabajo pasa por nuestro outbox. */
    private static final Set<String> OUTBOX_CHANNELS = Set.of("paymentEvents");

    /**
     * Espera a que no queden filas de {@code outbox_event} sin publicar para este
     * destino. Ver el comentario de {@link #purgeMessages}: purgar sin esperar deja que
     * un evento del escenario anterior aterrice después de la purga.
     */
    private static void awaitOutboxDrained(String destination) {
        // Un canal que este servicio no publica no pasa por nuestro outbox: no hay relay
        // que pueda entregar tarde, así que no hay nada que esperar.
        if (!OUTBOX_CHANNELS.contains(destination)) {
            return;
        }
        // Y con el broker parado POR EL PROPIO ESCENARIO no se espera: las filas están
        // pendientes a propósito —es toda la premisa del escenario del canal indisponible—
        // y el relay no puede drenarlas contra un broker caído. Esperar aquí no convergería
        // nunca: se agotaría el timeout entero en cada lectura de ese flujo.
        if (brokerIntentionallyStopped()) {
            return;
        }
        Instant deadline = Instant.now().plus(OUTBOX_DRAIN_TIMEOUT);
        while (Instant.now().isBefore(deadline) && pendingOutboxRows() > 0) {
            try {
                Thread.sleep(150L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando el drenaje del outbox", interrupted);
            }
        }
    }

    /**
     * Filas del outbox aún sin entregar.
     *
     * <p>Un fallo aquí NO se traduce a 0. Cero significa «drenado», así que tragarse el
     * error convierte una consulta rota en una espera que siempre pasa: los escenarios de
     * outbox seguirían corriendo, en verde, sin esperar a nada. Que se oiga.
     */
    private static int pendingOutboxRows() {
        try {
            String output = db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", "SELECT COUNT(*) FROM outbox_event WHERE destination = '" + OUTBOX_DESTINATION + "' AND published_at IS NULL");
            // Solo la ÚLTIMA línea: un cliente puede escribir avisos antes del resultado, y
            // concatenar sus dígitos daría un número enorme que parecería trabajo pendiente.
            String trimmed = output.trim();
            int lastBreak = Math.max(trimmed.lastIndexOf('\n'), trimmed.lastIndexOf('\r'));
            String digits = trimmed.substring(lastBreak + 1).replaceAll("[^0-9]", "");
            // Sin dígitos NO se devuelve cero. Cero significa «drenado», así que traducir una
            // lectura vacía a cero es exactamente lo que el javadoc de arriba prohíbe — y es lo
            // que estuvo pasando en la rama documental durante tres corridas: mongosh ejecutado
            // como archivo no autoimprimía, la salida venía vacía, y esta línea la leía como un
            // outbox drenado. La espera volvía al instante sin esperar a nada.
            if (digits.isEmpty()) {
                throw new IllegalStateException(
                        "La consulta de outbox_event no devolvió ningún número (salida: '" + trimmed + "'). "
                                + "Vacío NO es cero: si se tradujera a cero, la espera al drenaje volvería al "
                                + "instante y todo lo que dependa de ella mediría humo.");
            }
            return Integer.parseInt(digits);
        } catch (IllegalStateException loud) {
            // Ya trae su diagnóstico: reenvolverla con el mensaje genérico lo escondería.
            throw loud;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "No se pudo leer outbox_event para saber si el relay había drenado. Sin esa lectura la "
                            + "espera del outbox no espera a nada y lo que dependa de ella mide humo.",
                    e);
        }
    }

    /**
     * Deja la marca de espera de {@code activation} infinitamente rancia para la fila
     * {@code id}, de modo que el barrido la tome en <b>su próxima pasada</b>.
     *
     * <p>No despacha el barrido: lo adelanta en el cronómetro ({@link #fireTimer}) y lo ejecuta UNA réplica en su próxima pasada, como en producción. Lo que esto hace es
     * crear la condición que el barrido busca —«lleva esperando más de lo tolerado»— sin
     * esperar el plazo real, y solo para esta fila. Bajar el umbral por configuración sería
     * global y se llevaría por delante las filas de los demás escenarios, que están
     * esperando su desenlace normal.
     *
     * <p>Después, el {@code Then} es el de siempre: el estado que la API devuelve y el
     * mensaje que aparece en el canal. Deja margen para un tick del cron.
     *
     * <pre>{@code
     * ageForReconciliation("sweepPendingPayments", caseId);
     * await(Duration.ofSeconds(90), () -> "returnLost".equals(statusOf(caseId)));
     * }</pre>
     *
     * <p>Activaciones con barrido en este diseño: sweepPendingPayments.
     */
    protected static void ageForReconciliation(String activation, String id) {
        List<String> statements = new ArrayList<>();
        if ("sweepPendingPayments".equals(activation)) {
            statements.add("UPDATE payments SET awaiting_since = TIMESTAMP '1970-01-01 00:00:00' WHERE id = " + uuidLiteral(id));
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay barrido para la activación '" + activation + "'. Las que lo tienen: sweepPendingPayments");
        }
        for (String statement : statements) {
            db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", statement);
        }
        if ("sweepPendingPayments".equals(activation)) {
            fireTimer("schedule:sweepPendingPayments");
        }
    }

    /**
     * El inverso de {@code ageForReconciliation}: deja la marca de espera de {@code activation} en
     * el FUTURO para la fila {@code id}, de modo que el barrido <b>no</b> la tome aunque pasen sus
     * ciclos. Es la palanca de un escenario que pide «lo que acaba de entrar en vuelo no se toca»:
     * con el umbral de prueba en segundos y el cron en minutos, sin ella esa fila también estaría
     * rancia cuando llegue el ciclo, y el escenario no sería determinista.
     *
     * <pre>{@code
     * ageForReconciliation("sweepPendingPayments", atascado);
     * holdFromReconciliation("sweepPendingPayments", recienEnVuelo);
     * }</pre>
     */
    protected static void holdFromReconciliation(String activation, String id) {
        List<String> statements = new ArrayList<>();
        if ("sweepPendingPayments".equals(activation)) {
            statements.add("UPDATE payments SET awaiting_since = TIMESTAMP '2037-12-31 00:00:00' WHERE id = " + uuidLiteral(id));
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No hay barrido para la activación '" + activation + "'. Las que lo tienen: sweepPendingPayments");
        }
        for (String statement : statements) {
            db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", statement);
        }
    }

    /**
     * Cuántos eventos se rindió el outbox: agotaron sus reintentos y no salieron nunca.
     *
     * <p>El {@code Then} natural de casi cualquier escenario del outbox es que esto siga
     * valiendo <b>cero</b>: un relay que entrega tarde es correcto, uno que se rinde ha
     * perdido el evento. La aserción es barata y caza la diferencia, que ninguna otra
     * afirmación de la suite distingue — con el canal restablecido, el mensaje que nunca
     * salió y el que salió tarde se parecen mucho hasta que se cuenta.
     */
    protected long deadLetteredEvents() {
        Response response = get("/actuator/metrics/keel.outbox.dead_lettered", serviceCredential("checkout"));
        if (response.status() == 401 || response.status() == 403) {
            // El actuator no es público: si el diseño no dio con qué autenticarse, decirlo aquí
            // ahorra buscar el fallo en el relay, que es donde no está.
            throw new IllegalStateException(
                    "El actuator rechazó la lectura de keel.outbox.dead_lettered (" + response.status()
                            + "): la métrica exige credencial y este diseño no declara ni rol ni cliente máquina "
                            + "con el que pedirla.");
        }
        if (response.status() == 404) {
            // La métrica se registra al arrancar el relay: un 404 significa que no hay
            // outbox corriendo, no que no haya nada rendido. Decirlo evita leer un cero
            // tranquilizador de un servidor que no está midiendo nada.
            throw new IllegalStateException(
                    "El actuator no publica keel.outbox.dead_lettered: o el relay no arrancó, o "
                            + "'metrics' no está expuesto en el perfil con el que corre la suite.");
        }
        try {
            return JSON.readTree(response.body()).path("measurements").get(0).path("value").asLong();
        } catch (Exception e) {
            // Exception y no solo JacksonException: el get(0) de un array vacío es un null y el
            // path() siguiente un NullPointerException, que también es «respuesta inesperada».
            throw new IllegalStateException("Respuesta inesperada del actuator: " + response.body(), e);
        }
    }

    /**
     * Agota el presupuesto de reintentos de los eventos de ese tipo que siguen pendientes:
     * el relay los da por perdidos y deja de reclamarlos.
     *
     * <p>No espera a que ocurra —el presupuesto del perfil local es de 40 intentos— sino que
     * crea la condición que el relay dice que mira, igual que {@code ageForReconciliation}
     * fabrica el silencio de un barrido. El relay sigue corriendo por su cuenta.
     *
     * <p><b>Deja el contador sucio</b> hasta el siguiente reset: el cron de purga solo borra
     * lo publicado, así que la fila abandonada sobrevive dentro de la clase. Emparéjalo con
     * {@link #clearAbandonedOutboxEvents()} en un {@code finally}, o coloca el escenario el
     * ÚLTIMO de su clase — si no, los {@code deadLetteredEvents() == 0} de los siguientes
     * fallarán por culpa de este.
     */
    protected static void abandonOutboxEvent(String eventType) {
        db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", "UPDATE outbox_event SET attempts = 1000000 WHERE event_type = '" + eventType + "' AND published_at IS NULL");
    }

    /** Retira los eventos abandonados, para que el contador vuelva a cero. */
    protected static void clearAbandonedOutboxEvents() {
        db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", "DELETE FROM outbox_event WHERE published_at IS NULL AND attempts >= 1000000");
    }

    /**
     * API de gestión hasta el exchange, sin incluirlo: el destino se le concatena.
     *
     * <p>Se publica en el exchange del canal, <b>no</b> en {@code amq.default}: ese enruta por
     * nombre de COLA, y el canal de una suscripción es un exchange. Publicando ahí el mensaje
     * no llegaba a ninguna cola y RabbitMQ no se quejaba — 200 y {@code "routed":false}.
     */
    private static final String RABBIT_EXCHANGE_API = "http://rabbitmq:15672/api/exchanges/%2F/";

    /**
     * Entrega un mensaje <b>crudo</b> en un destino del broker real, como si lo hubiera
     * publicado el servicio de origen. Es la contrapartida de {@link #publishedMessages}.
     *
     * <p>El cuerpo viaja por archivo vía {@link #copyToDevtools}, nunca embebido en la
     * línea de comandos: en Windows el cliente de contenedores se come las comillas
     * dobles del JSON y lo que llega al broker es un cuerpo roto que el consumidor nunca
     * reconoce — el síntoma es un timeout mudo, no un error.
     *
     * <p>`key` es la clave de enrutado del broker, <b>no</b> la identidad del mensaje:
     * la que deduplica viaja donde el contrato del diseño diga (metadata, cabecera o
     * campo), y de eso se encarga cada `deliverXxx`.
     */
    protected static void deliverMessage(String destination, String key, String body, Map<String, String> headers) {
        // El cuerpo va en base64: incrustar un JSON dentro del campo `payload` (que es
        // una cadena JSON) exigiría escaparlo a mano, y ahí es donde se pierde un cuerpo.
        String request = "{\"properties\":{\"message_id\":\"" + key + "\",\"headers\":" + headersJson(headers) + "},\"routing_key\":\"" + key + "\",\"payload\":\"" + Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)) + "\",\"payload_encoding\":\"base64\"}";
        String bodyFile = devtoolsFile(DELIVER_BODY);
        String published;
        try {
            copyToDevtools(request, bodyFile);
            published = devtools("curl", "-sf", "-u", "guest:guest", "-H", "content-type: application/json", "-XPOST", "-d", "@" + bodyFile, RABBIT_EXCHANGE_API + destination + "/publish");
        } finally {
            removeFromDevtools(bodyFile);
        }
        // Publicar donde no hay binding que case NO es un error para RabbitMQ: contesta 200 y
        // `"routed":false`, así que el curl sale con 0 y la entrega parece hecha. El mensaje
        // no llega, el consumidor no reacciona, y el escenario muere mucho después en un
        // timeout que habla de otra cosa. Se falla aquí, con el destino delante.
        if (!published.contains("\"routed\":true")) {
            throw new IllegalStateException(
                    "RabbitMQ aceptó la publicación en '" + destination + "' pero no la enrutó a ninguna cola"
                            + " (routed:false): ¿existe el exchange y hay una cola bindeada? Respuesta: " + published);
        }
    }

    private static String headersJson(Map<String, String> headers) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(header.getKey()).append("\":\"").append(header.getValue()).append('"');
            first = false;
        }
        return json.append('}').toString();
    }

    /**
     * Ejecuta un comando dentro del contenedor devtools y devuelve su salida.
     *
     * <p>Los argumentos van siempre como <b>lista</b>, nunca concatenados en una
     * cadena para `sh -c`: en Windows, invocar `docker.exe`/`podman.exe` con una
     * cadena que lleva comillas escapadas hace que el cliente las reinterprete y
     * corrompa el comando antes de reenviarlo al contenedor (un cuerpo JSON llega
     * roto y el servidor responde 400 sin que se vea por qué). Para un pipeline de
     * verdad está {@link #devtoolsShell}, que lo hace explícito.
     */
    protected static String devtools(String... argv) {
        List<String> command = new ArrayList<>(List.of(containerRuntime(), "exec", DEVTOOLS_CONTAINER));
        command.addAll(List.of(argv));
        return runProcess(command);
    }

    /** Igual que {@link #devtools}, pero a través de un shell: pipes y redirecciones. */
    protected static String devtoolsShell(String command) {
        return devtools("sh", "-c", command);
    }

    /**
     * Copia un cuerpo al contenedor en vez de pasarlo como argumento: un JSON con
     * comillas dentro de la línea de comandos es exactamente lo que el cliente de
     * contenedores corrompe en Windows. `ProcessBuilder` reconstruye una única
     * cadena de línea de comandos a partir de la lista de argumentos, y su escapado
     * rompe las comillas dobles incrustadas antes de que `docker.exe`/`podman.exe`
     * las reciba — el cuerpo llega sin comillas y el fallo aparece lejos de su causa.
     */
    private static void copyToDevtools(String content, String target) {
        copyIntoContainer(DEVTOOLS_CONTAINER, content, ".json", target);
    }

    /**
     * Un archivo del contenedor PROPIO de esta llamada. Con uno fijo, dos entregas lanzadas a la vez (el escenario de
     * una carrera) copian su cuerpo al mismo sitio antes de publicarlo, y una se lleva el de la otra: con cuerpos
     * iguales no se nota, con distintos el escenario prueba otra cosa. Se borra con {@link #removeFromDevtools}.
     */
    private static String devtoolsFile(String prefix) {
        return prefix + "-" + UUID.randomUUID() + ".json";
    }

    /** Borra los archivos de una llamada; si no se puede, no es asunto del escenario (el contenedor es de prueba). */
    private static void removeFromDevtools(String... files) {
        try {
            List<String> argv = new ArrayList<>(List.of("rm", "-f"));
            argv.addAll(List.of(files));
            devtools(argv.toArray(String[]::new));
        } catch (RuntimeException ignored) {
            // Un /tmp con un archivo de más no cambia ningún resultado.
        }
    }

    /** Contenedor de RabbitMQ en `infra/docker-compose.yaml`. */
    private static final String BROKER_CONTAINER = "payment-rabbitmq";

    /** Espera máxima a que el broker vuelva a aceptar conexiones tras levantarlo. */
    private static final Duration BROKER_READY_TIMEOUT = Duration.ofSeconds(90);

    /**
     * Detiene el contenedor del broker. Es la palanca de los escenarios de
     * <b>outbox</b>: con el broker caído, una mutación tiene que responder igual y el
     * canal tiene que seguir vacío — ahí es donde un servicio que publica directo
     * dentro de la transacción se separa de uno que escribe en el outbox.
     *
     * <p><b>El escenario que lo llama tiene que restaurarlo</b>, y la forma correcta es
     * un {@code finally}: dejarlo caído envenena todos los flujos siguientes de la
     * misma suite, que fallarían por una causa ajena. Como red, {@link #resetState}
     * vuelve a levantarlo al principio de cada clase de flujo, así que un test que
     * muriera antes de su {@code finally} no arrastra el fallo más allá de su flujo.
     *
     * <p>No sustituye a nada: es infraestructura real parándose, no un doble.
     */
    protected static void stopBroker() {
        runProcess(List.of(containerRuntime(), "stop", BROKER_CONTAINER));
        // A partir de aquí, un fallo de TRANSPORTE al leer el canal es el efecto
        // buscado, no una infraestructura rota: ver {@link #brokerIntentionallyStopped}.
        BROKER_STOPPED.set(true);
    }

    /**
     * Levanta el contenedor del broker y <b>espera a que acepte conexiones</b>. Las dos
     * mitades importan: que el contenedor esté arrancado no es que el broker sirva, y
     * seguir sin esperar deja al escenario afirmando sobre un canal que todavía no
     * responde. El sondeo es el mismo que usa `infra/validate-infra.sh`.
     */
    protected static void startBroker() {
        runProcess(List.of(containerRuntime(), "start", BROKER_CONTAINER));
        awaitBrokerReady();
        // Se limpia DESPUÉS del sondeo: entre el `start` y el primer listener que
        // responde el broker sigue sin servir, y una lectura ahí tiene que tolerarse
        // igual que durante la parada.
        BROKER_STOPPED.set(false);
    }

    /**
     * ¿Tiró el broker el propio escenario? Es la diferencia entre el fallo que se
     * tolera y el que tiene que doler.
     *
     * <p>Leer el canal con el broker parado falla por <b>transporte</b>, no por «destino
     * desconocido», así que la tolerancia que existe para el destino que aún no se ha
     * creado no cubre este caso — y sin cubrirlo, el `Then` que afirma que <b>el canal
     * sigue vacío durante la caída</b> no es asertable. Ese `Then` es justo la mitad
     * negativa que separa un outbox de una publicación en línea: sin él, el escenario
     * del canal indisponible lo pasa igual un servidor que no tiene outbox ninguno.
     *
     * <p>Lo que NO se tolera, y por eso esto es un flag y no un `catch` ancho: una
     * infraestructura caída por su cuenta sigue reventando la suite en el sitio donde
     * se cae. Solo se perdona la indisponibilidad que el escenario provocó a propósito
     * y de la que es responsable de recuperarse.
     */
    private static final AtomicBoolean BROKER_STOPPED = new AtomicBoolean(false);

    /**
     * Restaura el broker si algún escenario lo dejó caído — y <b>solo</b> entonces.
     *
     * <p>El reset abre CADA clase de flujo, así que esto corre decenas de veces por
     * suite. Levantar un contenedor ya arrancado es barato, pero lo que cuelga de
     * {@link #startBroker()} no lo es: con un broker cuya topología no sobrevive al
     * reinicio, ahí dentro hay una resiembra y una sonda de entrega end-to-end que
     * espera hasta {@link #BROKER_READY_TIMEOUT}. Hacerlas cuando nadie tiró el broker
     * es trabajo inútil que, bajo la carga de la suite completa, agota su espera y mata
     * la clase entera por una causa que no es la suya.
     *
     * <p>El flag ya existe y es fiable: los tests corren en una sola JVM (sin
     * {@code forkEvery}), así que sobrevive entre clases. Si el escenario que lo tiró no
     * llegó a su `finally`, el flag sigue en `true` y esto lo restaura igual — que es
     * justo para lo que está.
     */
    private static void restoreBroker() {
        if (BROKER_STOPPED.get()) {
            startBroker();
        }
    }

    /**
     * Confirma que el broker parado por {@link #stopBroker()} <b>ya no acepta
     * conexiones</b>, en vez de darlo por hecho porque el comando de parada haya
     * devuelto el control.
     *
     * <p><b>Para qué existe.</b> Un escenario que entrega un evento ENTRANTE —que
     * necesita el broker arriba— y acto seguido tumba el canal de SALIDA tiene una
     * ventana de carrera real: el {@code stop} del contenedor tarda en cortar
     * conexiones de verdad, y lo que publique por su cuenta en esa ventana llega. El
     * escenario que afirma «el canal sigue vacío con el broker caído» pasaría por
     * casualidad de timing y no porque el mecanismo lo garantice.
     *
     * <p>Se llama <b>después</b> de {@link #stopBroker()}, y solo cuando el escenario
     * necesita esa certeza antes de soltar algo que publica solo.
     */
    protected static void awaitBrokerStopped() {
        Instant deadline = Instant.now().plus(BROKER_READY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!brokerAccepts()) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a que el broker se detenga", e);
            }
        }
        throw new IllegalStateException(
                "RabbitMQ sigue aceptando conexiones " + BROKER_READY_TIMEOUT
                        + " después de stopBroker(): la parada no surtió efecto real.");
    }


    private static void awaitBrokerReady() {
        Instant deadline = Instant.now().plus(BROKER_READY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (brokerAccepts()) {
                return;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando al broker", e);
            }
        }
        throw new IllegalStateException("RabbitMQ no volvió a aceptar conexiones en " + BROKER_READY_TIMEOUT);
    }

    /**
     * Sondeo de disponibilidad. Aquí el fallo <b>no</b> es un error: mientras el broker
     * arranca, el comando falla porque tiene que fallar. Por eso es el único sitio del
     * arnés donde se traga la excepción de {@link #runProcess}.
     */
    private static boolean brokerAccepts() {
        try {
            devtoolsShell("curl -sf -u guest:guest http://rabbitmq:15672/api/healthchecks/node");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // -- Segunda réplica ------------------------------------------------------

    private static Process REPLICA;
    private static int REPLICA_PORT;

    /** Espera máxima a que la réplica acepte tráfico. Arrancar Spring no es instantáneo. */
    private static final Duration REPLICA_READY_TIMEOUT = Duration.ofSeconds(120);

    /** Espera máxima a que el árbol de la réplica termine de morir tras el kill. */
    private static final Duration REPLICA_DEATH_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Arranca una segunda instancia del servicio contra la MISMA infraestructura y
     * devuelve su puerto.
     *
     * <p>Es la palanca de los escenarios de clúster: con dos procesos vivos hay dos
     * relays del outbox y dos barridos compitiendo por las mismas filas, y una
     * peticion puede dirigirse a una réplica u otra. Sin esto, "lo arbitra la clave
     * primaria" y "cada réplica se lleva un lote disjunto" son afirmaciones razonadas
     * que ningún escenario toca.
     *
     * <p><b>El escenario que la arranca tiene que pararla</b>, en un {@code finally}:
     * una réplica viva sigue publicando y barriendo durante los flujos siguientes, que
     * fallarian por una causa ajena. Como red, {@link #resetState} la para al abrir
     * cada clase.
     *
     * <p>Requiere el jar en {@code build/libs}: lo deja {@code infra/score-scenarios.sh},
     * que ejecuta {@code bootJar} antes de la suite.
     */
    protected static int startReplica() {
        if (REPLICA != null && REPLICA.isAlive()) {
            return REPLICA_PORT;
        }
        Path jar = bootJar();
        REPLICA_PORT = freePort();
        Path log = Path.of("build", "keel-replica.log");
        try {
            Files.createDirectories(log.getParent());
            // La salida va a un archivo y no al pipe del proceso: un arranque de Spring
            // llena el búfer del pipe y, sin nadie leyéndolo, la réplica se queda
            // bloqueada escribiendo. Un cuelgue que parece un arranque lento.
            REPLICA = new ProcessBuilder(
                            javaExecutable(),
                            "-jar",
                            jar.toString(),
                            "--spring.profiles.active=local",
                            "--server.port=" + REPLICA_PORT,
                            // El endpoint de apagado, abierto SOLO para este proceso y por
                            // línea de comandos: la configuración de la aplicación no lo
                            // expone y no tiene por qué. Es lo que permite que
                            // stopReplica() cierre ordenadamente en cualquier sistema
                            // operativo — ver su javadoc, que explica qué se rompe sin esto.
                            // Solo 'access', que es la forma de Boot 3.4+ y la que corresponde a
                            // la versión que fija este generador. NO añadir también el 'enabled'
                            // de las versiones anteriores «por si acaso»: Boot las trata como
                            // MUTUAMENTE EXCLUYENTES y aborta el arranque con «Update your
                            // configuration so that only one of the mutually exclusive properties
                            // is configured». La réplica no llega a levantar y el escenario de
                            // clúster falla con «murió durante el arranque».
                            "--management.endpoints.web.exposure.include=health,shutdown",
                            "--management.endpoint.shutdown.access=unrestricted",
                            // Marca de la réplica en su línea de comandos: es lo que permite
                            // reconocerla por su PID en la ejecución SIGUIENTE sin matar a un
                            // proceso ajeno que hubiera heredado el número.
                            REPLICA_MARKER)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo arrancar la segunda réplica desde " + jar, e);
        }
        writeReplicaPid(REPLICA.pid());
        awaitReplicaReady(log);
        return REPLICA_PORT;
    }

    /**
     * Para la réplica, <b>ordenadamente</b>. Idempotente: sobre una ya parada no hace nada.
     *
     * <p><b>Por qué el rodeo del actuator y no {@code destroy()} a secas.</b> En Linux
     * {@code Process.destroy()} manda un SIGTERM y Spring ejecuta su cierre: para los
     * contenedores de escucha, devuelve al broker lo que tuviera en vuelo y suelta el pool.
     * En Windows {@code destroy()} es {@code TerminateProcess} — un kill duro, sin hooks de
     * apagado. La réplica muere consumiendo, y cada mensaje que tuviera recibido se queda
     * <b>invisible</b> hasta que vence el {@code VisibilityTimeout} de su cola (60 s), muy por
     * encima del plazo de cualquier {@code await} de un escenario.
     *
     * <p>El síntoma no se parece en nada a su causa: el escenario SIGUIENTE entrega un mensaje,
     * nadie lo procesa dentro de su plazo y lo que se ve es «un estado no transicionó a tiempo»,
     * sin excepción, sin DLQ y sin pérdida — el mensaje sigue en la cola, invisible. Se diagnosticó
     * en la corrida {@code refunds-http} deshabilitando el escenario de clúster: sin él, el de al lado
     * pasaba; con él, fallaba las tres veces.
     *
     * <p>El endpoint de apagado lo abre {@link #startReplica()} por línea de comandos y solo para
     * la réplica: la configuración de la aplicación no lo expone, y este proceso es de usar y
     * tirar. El {@code destroy()} sigue detrás como red — una réplica que no se puede parar por
     * las buenas tiene que morir igual.
     */
    protected static void stopReplica() {
        if (REPLICA == null) {
            // REPLICA es estático: una réplica que sobrevivió a OTRA ejecución de la suite (un
            // Gradle matado a medias) no está aquí, y seguía reclamando filas contra la misma base.
            // Su PID quedó en build/keel-replica.pid (corrida asset-vault R8).
            killOrphanReplica();
            return;
        }
        ProcessHandle raiz = REPLICA.toHandle();
        List<ProcessHandle> arbol = replicaTree(raiz);

        requestReplicaShutdown();
        try {
            // Al apagado ordenado se le da un plazo corto: si no ha muerto, es que el endpoint
            // no respondió o el cierre se atascó, y entonces vale más matarla que esperarla.
            if (!REPLICA.waitFor(15, TimeUnit.SECONDS)) {
                REPLICA.destroy();
                if (!REPLICA.waitFor(30, TimeUnit.SECONDS)) {
                    REPLICA.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            REPLICA.destroyForcibly();
        }
        killTree(raiz, arbol);
        awaitReplicaDead(arbol);
        REPLICA = null;
        deleteReplicaPid();
    }

    /** El sello de la línea de comandos de la réplica (ver {@link #startReplica()}). */
    private static final String REPLICA_MARKER = "--keel.harness.replica=true";

    private static final Path REPLICA_PID = Path.of("build", "keel-replica.pid");

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /**
     * El árbol a matar. En Windows NO se enumera con {@code descendants()}: ahí se sospecha de él
     * en el {@code 0xC0000005} de la corrida asset-vault R8, y {@code taskkill /T} ya mata el árbol
     * entero desde la raíz. En el resto, se captura ANTES de matar: muerto el padre, sus hijos ya no
     * se pueden enumerar desde él.
     */
    private static List<ProcessHandle> replicaTree(ProcessHandle raiz) {
        List<ProcessHandle> arbol = new ArrayList<>();
        if (!isWindows()) {
            arbol.addAll(raiz.descendants().toList());
        }
        arbol.add(raiz);
        return arbol;
    }

    private static void killTree(ProcessHandle raiz, List<ProcessHandle> arbol) {
        if (isWindows()) {
            if (raiz.isAlive()) {
                try {
                    new ProcessBuilder("taskkill", "/PID", String.valueOf(raiz.pid()), "/T", "/F")
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start()
                            .waitFor(30, TimeUnit.SECONDS);
                } catch (IOException e) {
                    raiz.destroyForcibly();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    raiz.destroyForcibly();
                }
            }
            return;
        }
        for (ProcessHandle vivo : arbol) {
            if (vivo.isAlive()) {
                vivo.destroyForcibly();
            }
        }
    }

    private static void writeReplicaPid(long pid) {
        try {
            Files.writeString(REPLICA_PID, String.valueOf(pid));
        } catch (IOException e) {
            // Sin el archivo solo se pierde la red contra huérfanos de la ejecución siguiente.
        }
    }

    private static void deleteReplicaPid() {
        try {
            Files.deleteIfExists(REPLICA_PID);
        } catch (IOException e) {
            // Un PID viejo no hace daño: killOrphanReplica comprueba la marca antes de matar.
        }
    }

    /**
     * Mata la réplica de una ejecución anterior si su PID sigue vivo Y es de verdad la réplica: la
     * marca de su línea de comandos lo distingue de un proceso que hubiera heredado el número.
     */
    private static void killOrphanReplica() {
        if (!Files.exists(REPLICA_PID)) {
            return;
        }
        try {
            long pid = Long.parseLong(Files.readString(REPLICA_PID).trim());
            java.util.Optional<ProcessHandle> huerfana = ProcessHandle.of(pid)
                    .filter(ProcessHandle::isAlive)
                    .filter(handle -> handle.info().commandLine().map(line -> line.contains(REPLICA_MARKER)).orElse(false));
            if (huerfana.isPresent()) {
                List<ProcessHandle> arbol = replicaTree(huerfana.get());
                killTree(huerfana.get(), arbol);
                awaitReplicaDead(arbol);
            }
        } catch (IOException | NumberFormatException e) {
            // Archivo ilegible: no hay nada fiable que matar.
        }
        deleteReplicaPid();
    }

    /**
     * Comprueba que el árbol de la réplica murió DE VERDAD, y si no, lo dice.
     *
     * <p>{@code destroyForcibly()} es asíncrono por contrato de la API: devuelve el control sin
     * garantizar que el proceso haya terminado. Sin esta comprobación, {@code stopReplica()}
     * devolvía y ponía {@code REPLICA = null} incondicionalmente — con lo que una réplica
     * superviviente quedaba <b>huérfana</b>: la guarda de arriba hace que cualquier
     * {@code stopReplica()} posterior devuelva de inmediato, y ya no hay quien la pare.
     *
     * <p>Lo que eso produce no es un error, es peor. La réplica sigue viva contra la MISMA base,
     * reclamando filas con {@code SKIP LOCKED} y barriendo, mientras corren los flujos siguientes.
     * Ocurrió: sobrevivió más de hora y media y tumbó ocho escenarios de cinco clases sin relación
     * funcional entre sí, todos con el mismo síntoma —«no llegó nada dentro del plazo»— y ninguno
     * apuntando a su causa.
     *
     * <p>Por eso <b>lanza</b> en vez de devolver en silencio: una réplica que no se puede matar
     * invalida todo lo que venga después, y es mejor un rojo que nombra el PID y el log que treinta
     * rojos que no se parecen a nada. Morir es correcto; morir callado no.
     *
     * <p><b>Lo que esto NO garantiza</b>, dicho para que no se dé por hecho: que en Windows el
     * árbol de descendientes que devuelve {@code ProcessHandle} sea completo. Se ha razonado, no
     * medido — nadie ha arrancado y matado una réplica de verdad para comprobarlo.
     */
    private static void awaitReplicaDead(List<ProcessHandle> arbol) {
        Instant deadline = Instant.now().plus(REPLICA_DEATH_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            List<ProcessHandle> vivos = arbol.stream().filter(ProcessHandle::isAlive).toList();
            if (vivos.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando la muerte de la réplica", e);
            }
        }
        String pids = arbol.stream()
                .filter(ProcessHandle::isAlive)
                .map(handle -> String.valueOf(handle.pid()))
                .collect(java.util.stream.Collectors.joining(", "));
        throw new IllegalStateException(
                "La réplica sigue viva tras pedirle el cierre y matarla (PID " + pids + "). Comparte base con el "
                        + "resto de la suite y seguirá reclamando filas: mátala a mano antes de volver a ejecutar. "
                        + "Revisa build/keel-replica.log");
    }

    /**
     * Pide el cierre ordenado por el actuator. No lanza: si el endpoint no está o la réplica ya
     * no acepta tráfico, el que manda es el {@code destroy()} de quien llama.
     */
    private static void requestReplicaShutdown() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create("http://localhost:" + REPLICA_PORT + "/actuator/shutdown").toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);
            try {
                connection.getResponseCode();
            } finally {
                connection.disconnect();
            }
        } catch (IOException yaNoAtiende) {
            // La réplica ya no atiende: no hay cierre ordenado que pedir.
        }
    }

    /**
     * Petición dirigida a la SEGUNDA réplica, no a la que arranca JUnit. Es lo que
     * permite que dos peticiones simultáneas con la misma clave lleguen a procesos
     * distintos: el caso que el registro de idempotencia existe para cerrar y el
     * unico que dos hilos de esta JVM no reproducen.
     */
    protected Response onReplica(HttpMethod method, String path, String jsonBody, String token, String idempotencyKey) {
        if (REPLICA == null || !REPLICA.isAlive()) {
            throw new IllegalStateException("La réplica no está arrancada: llama antes a startReplica()");
        }
        return exchange(method, "http://localhost:" + REPLICA_PORT + path, jsonBody, token, idempotencyKey);
    }

    /** Localiza el jar ejecutable, descartando el -plain que Boot genera al lado. */
    private static Path bootJar() {
        Path libs = Path.of("build", "libs");
        try (Stream<Path> files = Files.list(libs)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-plain.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No hay jar ejecutable en " + libs + ": ejecuta ./gradlew bootJar"));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se puede leer " + libs + ": ejecuta ./gradlew bootJar antes de la suite", e);
        }
    }

    /** El mismo java que corre esta suite: no se depende de que haya uno en el PATH. */
    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No hay puerto libre para la réplica", e);
        }
    }

    private static void awaitReplicaReady(Path log) {
        Instant deadline = Instant.now().plus(REPLICA_READY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!REPLICA.isAlive()) {
                // Ya está muerta: la referencia sobra, y dejarla haría que el siguiente
                // stopReplica() creyera que hay algo que parar.
                REPLICA = null;
                throw new IllegalStateException(
                        "La réplica murió durante el arranque. Revisa " + log.toAbsolutePath());
            }
            if (replicaAccepts()) {
                return;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a la réplica", e);
            }
        }
        stopReplica();
        throw new IllegalStateException(
                "La réplica no respondió en " + REPLICA_READY_TIMEOUT + ". Revisa " + log.toAbsolutePath());
    }

    private static boolean replicaAccepts() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create("http://localhost:" + REPLICA_PORT + "/actuator/health/readiness").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            try {
                return connection.getResponseCode() == 200;
            } finally {
                connection.disconnect();
            }
        } catch (IOException notYet) {
            return false;
        }
    }

    private static final String DB_CONTAINER = "payment-devtools";

    /**
     * Ejecuta una CLI contra la base de prueba y devuelve su salida. Argumentos
     * siempre como <b>lista</b>, nunca concatenados en una cadena para `sh -c`: en
     * Windows el cliente de contenedores reinterpreta las comillas escapadas y
     * corrompe el comando antes de reenviarlo (ver {@link #devtools}).
     *
     * <p>Lo que no se ve por HTTP y tampoco es un mensaje: que una escritura llegó de
     * verdad al almacén, o que un borrado lógico dejó el documento donde debía. No es
     * la vía por defecto —si el propio servicio lo expone por su API, se comprueba por
     * ahí, que es lo que hace un cliente—, pero es la única para un efecto que ninguna
     * operación del diseño devuelve.
     *
     * <p><b>Toda sentencia va por aquí</b>, y cualquier helper propio que escribas
     * encima también: el motor elegido (PostgreSQL) se invoca así, y la sentencia
     * entra como <b>un elemento más</b> del argv.
     * <pre>db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c",
     *    "SELECT id FROM la_tabla WHERE slug = 'ejemplo'");</pre>
     *
     * <p><b>Valores tal como se GUARDAN.</b> La columna lleva el nombre de la constante, no el
     * literal del diseño que viaja en JSON: el que ves en `specs/` y en `openapi.yaml` es el
     * del cable. Un WHERE con el literal no casa ninguna fila, y un UPDATE con él choca contra la
     * restricción de la columna y se lleva la clase entera desde su `@BeforeAll`.
     * <pre>
     * PaymentStatus: PENDING, ACTION_REQUIRED, AUTHORIZED, CAPTURING, CANCELING, CAPTURED, REFUNDING, REFUNDED, CANCELED, FAILED
     * FailureReason: DECLINED, INSUFFICIENT_FUNDS, EXPIRED_CARD, AUTHENTICATION_FAILED, FRAUD_SUSPECTED, INVALID_PAYMENT_METHOD, NOT_RECEIVED, PROCESSING_ERROR
     * RefundStatus: PENDING, SUCCEEDED, FAILED
     * PaymentMethodStatus: ACTIVE, REMOVED
     * PaymentCancelOrigin: AUTHORIZED, ACTION_REQUIRED
     * </pre>
     */
    protected static String db(String... argv) {
        List<String> command = new ArrayList<>(List.of(containerRuntime(), "exec", DB_CONTAINER));
        command.addAll(List.of(argv));
        return runProcess(command);
    }


    /**
     * Un UUID escrito para una sentencia de {@link #db}, con la forma que pide PostgreSQL.
     *
     * <p>No es azucarillo: aquí la columna no es texto, y un literal en texto plano no casa
     * con ninguna fila <b>ni da error</b> — el WHERE sale vacío y el INSERT guarda algo que
     * luego no encuentra nadie. El síntoma no se parece a un SQL mal escrito, se parece a un
     * servicio que no hizo su trabajo.
     *
     * <pre>db(..., "SELECT status FROM &lt;tabla&gt; WHERE id = " + uuidLiteral(id));</pre>
     *
     * <p>Y la otra que se adivina mal: la columna del bloqueo optimista se llama
     * {@code lock_version}, no {@code version}. Una fila sembrada sin ella (o con el nombre
     * equivocado) falla al primer UPDATE del servicio.
     */
    protected static String uuidLiteral(String id) {
        return "'" + id + "'";
    }


    /**
     * Igual que {@link #db}, pero a través de un shell: <b>solo</b> para lo que es del
     * shell —un pipe, una redirección, un prefijo de entorno que no se pueda resolver
     * con `env`—. La invocación de sondeo del motor, la misma que usa
     * `infra/validate-infra.sh`:
     * <pre>dbShell("PGPASSWORD='changeme' psql -h db -U payment -d payment -c 'SELECT 1' -q -t");</pre>
     *
     * <p><b>Una sentencia con comillas o con valores interpolados NO se arma como
     * cadena para este método</b>, por cómodo que resulte copiar la línea de arriba:
     * pasa por dos intérpretes (el cliente de contenedores y luego `sh`) y en Windows
     * el primero se la come — el síntoma es un error de sintaxis del motor sobre un
     * fragmento suelto de tu SQL, y como el fixture suele vivir en un `@BeforeAll`,
     * cae la clase entera con `initializationError`. Para eso está {@link #db}.
     */
    protected static String dbShell(String command) {
        return db("sh", "-c", command);
    }

    /**
     * Adelanta el temporizador recurrente {@code kind} del cronómetro: vence YA, con la hora de la base, y sin arriendo.
     *
     * <p>No dispara nada: el sondeador lo recoge en su próxima pasada (como mucho {@code timer.max-sleep-ms}), en UNA
     * réplica, como en producción. Es lo que hace que un escenario de barrido no espere al cron. Los helpers que fabrican
     * la precondición de un rescate o de una reconciliación ya lo llaman por ti.
     *
     * <pre>{@code
     * fireTimer("schedule:sweepPendingPayments");
     * await(Duration.ofSeconds(45), () -> ...);
     * }</pre>
     *
     * <p>Recurrentes de este servicio: purge:keel_timer, purge:outbox_event, purge:idempotency_record, schedule:sweepPendingPayments.
     */
    protected static void fireTimer(String kind) {
        db("env", "PGPASSWORD=changeme", "psql", "-h", "db", "-U", "payment", "-d", "payment", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-c", "UPDATE keel_timer SET due_at = current_timestamp, claimed_until = NULL, claim_token = NULL WHERE kind = " + "'" + kind.replace("'", "''") + "'" + "");
    }

    private static String containerRuntime;

    /**
     * Copia contenido al contenedor como ARCHIVO, en vez de pasarlo como argumento.
     *
     * <p>Es la única forma fiable de que un texto CON COMILLAS llegue intacto: en Windows,
     * {@code ProcessBuilder} reconstruye una sola línea de comandos a partir de la lista y su
     * escapado rompe las comillas dobles incrustadas antes de que {@code docker.exe} /
     * {@code podman.exe} las reciba. El contenido llega sin comillas y el fallo aparece muy
     * lejos de su causa — un {@code ReferenceError} del intérprete de destino sobre un
     * fragmento suelto de lo que escribiste.
     */
    private static void copyIntoContainer(String container, String content, String suffix, String target) {
        try {
            Path temp = Files.createTempFile("keel-", suffix);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            runProcess(List.of(containerRuntime(), "cp", temp.toString(), container + ":" + target));
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo copiar el contenido al contenedor " + container, e);
        }
    }

    /**
     * Ejecuta el proceso, <b>exige código de salida 0</b> y deja la evidencia en
     * {@link FailureCapture}. Ignorar el código es lo que convierte un `curl -sf`
     * fallido en una cadena vacía y hace que el error aparezca mucho más tarde y muy
     * lejos de su causa.
     */
    private static String runProcess(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            FailureCapture.recordProbe(command, exit, output);
            if (exit != 0) {
                throw new IllegalStateException("Falló el sondeo de infraestructura (código " + exit + "): "
                    + String.join(" ", command) + System.lineSeparator() + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar el comando en el contenedor: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido hablando con el contenedor", e);
        }
    }

    /**
     * Runtime de contenedores, con la misma resolución que los scripts de `infra/`:
     * `CONTAINER_RUNTIME` y, si no está, el primero disponible entre docker y podman.
     * Caer a "docker" a secas dejaba la suite muerta en una máquina solo con podman.
     */
    private static synchronized String containerRuntime() {
        if (containerRuntime == null) {
            containerRuntime = detectContainerRuntime();
        }
        return containerRuntime;
    }

    private static String detectContainerRuntime() {
        String configured = System.getenv("CONTAINER_RUNTIME");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        for (String candidate : List.of("docker", "podman")) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                if (process.waitFor() == 0) {
                    return candidate;
                }
            } catch (IOException e) {
                // Ese runtime no está en el PATH: se prueba el siguiente.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido detectando el runtime de contenedores", e);
            }
        }
        throw new IllegalStateException("No se encontró docker ni podman en el PATH. Exporta CONTAINER_RUNTIME con el que uses.");
    }


    /**
     * El token cacheado de esa clave, pedido de nuevo si ya no sirve.
     *
     * <p>La caché sin caducidad era un fallo con forma de 401 sin relación con nada: esta clase
     * es {@code PER_CLASS}, así que el token vive lo que dure la clase entera, y el realm de
     * prueba emite tokens de <b>cinco minutos</b> (el default de Keycloak; el aprovisionamiento
     * no fija {@code accessTokenLifespan}). Cualquier flujo con esperas largas —un rescate, una
     * reconciliación— pasa de ahí y empieza a mandar credenciales caducadas a mitad de clase.
     *
     * <p>Se renueva con margen sobre el claim {@code exp}, sin librerías: el payload de un JWT es
     * Base64 URL. Un token cuyo {@code exp} no se pueda leer se da por caducado — pedir otro
     * cuesta una llamada; usar uno inservible cuesta un escenario en rojo por el motivo equivocado.
     */
    private String cachedToken(String key, java.util.function.Supplier<String> mint) {
        String cached = credentials.get(key);
        if (cached != null && !expiresWithin(cached, TOKEN_RENEWAL_MARGIN)) {
            return cached;
        }
        String fresh = mint.get();
        credentials.put(key, fresh);
        return fresh;
    }

    /** ¿Le quedan a este token menos de {@code margin} de vida? */
    private static boolean expiresWithin(String token, Duration margin) {
        try {
            // Por índices y no con split("\."): un regex aquí obliga a escapar el punto dos
            // veces (una para la plantilla, otra para Java) y es donde se cuela el escape roto.
            int firstDot = token.indexOf('.');
            int secondDot = firstDot < 0 ? -1 : token.indexOf('.', firstDot + 1);
            if (secondDot < 0) {
                return true;
            }
            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(token.substring(firstDot + 1, secondDot)),
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = EXP_CLAIM.matcher(payload);
            if (!matcher.find()) {
                return true;
            }
            Instant expiry = Instant.ofEpochSecond(Long.parseLong(matcher.group(1)));
            return Instant.now().plus(margin).isAfter(expiry);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Credencial de máquina (client_credentials) del cliente declarado en
     * security.serviceClients: los escenarios `level: service` no usan token de
     * usuario.
     *
     * <p>El secreto sale de `infra/test-credentials.env`, que es donde lo dejó el
     * aprovisionamiento: primero la entrada del cliente
     * (`AUTH_CLIENT_SECRET_&lt;CLIENTE&gt;`), luego el default `AUTH_CLIENT_SECRET`.
     * Aquí no se inventa ningún literal — que las pruebas y la infraestructura
     * adivinasen cada una su secreto es exactamente lo que bloqueaba la suite entera.
     */
    protected String serviceCredential(String client) {
        return cachedToken("client:" + client, () ->
            requestToken("grant_type=client_credentials"
                + "&client_id=" + client
                + "&client_secret=" + clientSecret(client)));
    }

    private static String clientSecret(String client) {
        String key = "AUTH_CLIENT_SECRET_" + client.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        String specific = env(key, null);
        // Sin archivo de credenciales, la convención del secreto de un cliente del
        // diseño sigue siendo derivable: '<cliente>-secret' (auth-provisioning.js).
        return specific != null ? specific : env("AUTH_CLIENT_SECRET", client + "-secret");
    }

    private String requestToken(String form) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(env("AUTH_TOKEN_URL", "http://localhost:8180/realms/payment/protocol/openid-connect/token")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("El proveedor de identidad devolvió " + response.statusCode() + ": " + response.body());
            }
            return JsonPath.read(response.body(), "$.access_token");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo obtener el token", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido obteniendo el token", e);
        }
    }

    /**
     * Credenciales del proveedor de identidad, por orden: variable de entorno →
     * `infra/test-credentials.env` → valor convencional.
     *
     * <p>El archivo lo escribe `keel-spring build` junto al script de
     * aprovisionamiento, así que los nombres de cliente y los secretos tienen un
     * <b>único productor</b>. Antes cada lado los hardcodeaba por su cuenta y el
     * desajuste no se veía hasta ejecutar la suite completa.
     */
    private static final Map<String, String> PROVISIONED = loadProvisionedCredentials();

    private static Map<String, String> loadProvisionedCredentials() {
        Path path = Path.of("infra", "test-credentials.env");
        if (!Files.isReadable(path)) {
            return Map.of();
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator > 0) {
                    values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
                }
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer infra/test-credentials.env", e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = PROVISIONED.get(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
