package com.ecomerce.notifications.flows;

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
    @Order(3)
    @DisplayName("SMOKE-3: el proveedor de identidad emite credenciales")
    void issuesCredentials() {
        Assertions.assertFalse(tokenFor("notifications-admin").isBlank(),
            "El proveedor de identidad no devolvió token para el rol 'notifications-admin'.");

        Assertions.assertFalse(serviceCredential("billing").isBlank(),
            "No hay credencial de máquina para el cliente 'billing': revisa infra/test-credentials.env.");
    }

    @Test
    @Order(4)
    @DisplayName("SMOKE-4: cada canal de publicación se lee y se purga")
    void eventChannelsAreReadableAndPurgeable() {
        for (String channel : List.of("notificationEvents")) {
            Assertions.assertDoesNotThrow(() -> publishedMessages(channel, 1),
                "No se pudo leer el canal '" + channel + "': la topología no está declarada o el sondeo está roto.");
            Assertions.assertDoesNotThrow(() -> purgeMessages(channel),
                "No se pudo purgar el canal '" + channel + "': las aserciones de mensajería leerían mensajes de la corrida anterior.");
            Assertions.assertTrue(publishedMessages(channel, 1).trim().equals("[]"),
                "El canal '" + channel + "' sigue entregando mensajes después de purgarlo.");
        }
    }

    @Test
    @Order(7)
    @DisplayName("SMOKE-7: el buzón de prueba responde y el reset lo deja vacío")
    void mailSinkIsReachable() {
        Assertions.assertEquals(0, mailCount("humo@keel.test"),
            "El buzón no arranca vacío: un correo de la corrida anterior haría que el primer awaitMailTo de un flujo devolviera el mensaje equivocado.");
    }
}
