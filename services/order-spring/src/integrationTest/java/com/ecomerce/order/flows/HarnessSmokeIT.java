package com.ecomerce.order.flows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Humo del <b>arnés</b>, no del servicio: comprueba que la fontanería de
 * {@link AbstractFlowIT} funciona contra la infraestructura levantada antes de
 * que nadie interprete un fallo de flujo como un fallo de negocio.
 *
 * <p>Es lo primero que ejecuta la fase de validación
 * (`./gradlew integrationTest --tests '*HarnessSmokeIT'`). En rojo, el problema
 * está en el arnés o en la infraestructura: no tiene sentido correr la suite ni
 * relanzar al agente de pruebas.
 *
 * <p>La genera `keel-spring build` y <b>no se edita</b> para hacerla pasar: si
 * falla, o falta infraestructura o el defecto es del generador.
 */
@DisplayName("Humo del arnés de pruebas")
class HarnessSmokeIT extends AbstractFlowIT {

    @Test
    @Order(1)
    @DisplayName("SMOKE-1: el reset de estado se ejecuta sin error")
    void resetsState() {
        Assertions.assertDoesNotThrow(AbstractFlowIT::resetState,
            "El reset de estado falló: sin él ningún flujo arranca con el Given que declara.");
    }

    @Test
    @Order(2)
    @DisplayName("SMOKE-2: el servidor responde")
    void serverResponds() {
        Response response = get("/actuator/health");
        Assertions.assertEquals(200, response.status(),
            "El servidor no responde en /actuator/health: " + response.body());
    }

    @Test
    @Order(4)
    @DisplayName("SMOKE-4: cada canal de publicación se lee y se purga")
    void eventChannelsAreReadableAndPurgeable() {
        for (String channel : List.of("notificationRequests")) {
            Assertions.assertDoesNotThrow(() -> publishedMessages(channel, 1),
                "No se pudo leer el canal '" + channel + "': la topología no está declarada o el sondeo está roto.");
            Assertions.assertDoesNotThrow(() -> purgeMessages(channel),
                "No se pudo purgar el canal '" + channel + "': las aserciones de mensajería leerían mensajes de la corrida anterior.");
            Assertions.assertTrue(publishedMessages(channel, 1).trim().equals("[]"),
                "El canal '" + channel + "' sigue entregando mensajes después de purgarlo.");
        }
    }

    @Test
    @Order(6)
    @DisplayName("SMOKE-6: el proveedor de prueba se programa, responde y se resetea")
    void httpStubIsProgrammable() {
        resetStubs();
        stubFor("GET", "/__keel-smoke", 200, "{\"ok\":true}");
        Assertions.assertEquals(1, probeStub(), "El stub no devolvió lo programado en /__keel-smoke.");
        Assertions.assertEquals(1, stubCallCount("GET", "/__keel-smoke"),
            "El stub no contabiliza las llamadas: las aserciones sobre cuántas veces se llamó al proveedor no valdrían.");
        List<String> requests = stubRequests("GET", "/__keel-smoke");
        Assertions.assertEquals(1, requests.size(),
            "El stub no devuelve el log de peticiones: no se podría afirmar QUÉ se envió al proveedor, solo cuántas veces.");
        Assertions.assertEquals("1", stubRequestHeader(requests.get(0), "x-keel-smoke"),
            "El log de peticiones no conserva las cabeceras: la aserción sobre la cabecera de idempotencia saliente no valdría.");
        resetStubs();
        Assertions.assertEquals(0, stubCallCount("GET", "/__keel-smoke"),
            "El reset no borra el log de peticiones: un flujo contaría llamadas del anterior.");
    }

    private int probeStub() {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:8090/__keel-smoke"))
                            // Cabecera propia: la busca el humo por su nombre en OTRO caso, que
                            // es como llegan las que pone un cliente HTTP de verdad.
                            .header("X-Keel-Smoke", "1")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"ok\"") ? 1 : 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError("El proveedor de prueba no responde en http://localhost:8090", e);
        }
    }
}
