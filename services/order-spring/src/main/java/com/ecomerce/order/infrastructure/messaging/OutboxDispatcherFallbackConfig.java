package com.ecomerce.order.infrastructure.messaging;

import com.ecomerce.order.infrastructure.messaging.outbox.OutboxDispatcher;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fallback del puerto de salida del outbox mientras no hay dispatcher real.
 *
 * Cede el sitio en cuanto exista otro bean de {@link OutboxDispatcher}: no hay que
 * borrar este archivo, hay que escribir el de verdad.
 */
@Configuration
public class OutboxDispatcherFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcherFallbackConfig.class);

    /** Perfiles en los que arrancar sin broker es legítimo. */
    private static final Set<String> TOLERATED = Set.of("local", "test");

    @Bean
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    public OutboxDispatcher outboxDispatcherStub(Environment environment) {
        List<String> active = List.of(environment.getActiveProfiles());
        if (!active.isEmpty() && active.stream().noneMatch(TOLERATED::contains)) {
            // El relay daría por publicada cada fila que le pase por aquí. Con
            // reliability: outbox eso no es degradarse, es perderlo todo en silencio.
            throw new IllegalStateException(
                "No hay implementación de OutboxDispatcher y el perfil activo es " + active
                    + ": el relay marcaría como publicados eventos que nunca salen del proceso. "
                    + "Impleméntalo con la skill keel-spring-<broker> del stack elegido en keel-stack.json.");
        }
        log.warn("OutboxDispatcher sin implementar: los eventos NO salen del proceso (perfil {})", active);
        return (destination, routingKey, eventType, payload) -> {
            // TODO (agente): sustituir este stub por el dispatcher real del broker
            //   elegido en keel-stack.json (skill keel-spring-<broker>):
            //   enviar el payload tal cual (ya es la EventEnvelope serializada) al
            //   destino/routing key indicados, con content-type application/json.
            log.warn("OutboxDispatcher no implementado: {} no salió a {}/{}", eventType, destination, routingKey);
        };
    }
}
