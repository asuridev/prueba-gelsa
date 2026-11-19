package com.ecomerce.order.infrastructure.telemetry;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Puebla {@link MessageTracing} con el tracer, el propagador W3C y el registro de observaciones
 * que construye Boot. Es la única vía por la que las piezas estáticas de la mensajería llegan a
 * la telemetría.
 *
 * <p>Todo por {@code ObjectProvider}, y no es prudencia genérica: con las trazas apagadas (el
 * perfil {@code test}, y cualquier {@code @SpringBootTest}, que Boot arranca sin trazas) Boot
 * aporta un {@code Tracer} no-op pero NINGÚN {@code Propagator}. Pedirlo por constructor haría
 * que el contexto no arrancara justo donde la telemetría no importa. Sin él, MessageTracing
 * degrada a no hacer nada.
 */
@Component
class MessageTracingInstaller {

    MessageTracingInstaller(
            ObjectProvider<Tracer> tracer,
            ObjectProvider<Propagator> propagator,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        MessageTracing.install(tracer.getIfAvailable(), propagator.getIfAvailable(), observationRegistry.getIfAvailable());
    }
}
