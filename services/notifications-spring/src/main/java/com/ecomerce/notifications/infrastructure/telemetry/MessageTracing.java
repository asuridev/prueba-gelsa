package com.ecomerce.notifications.infrastructure.telemetry;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contexto de traza W3C ({@code traceparent}) a través de los mensajes.
 *
 * <p>Viaja en {@code metadata.traceparent} de la envoltura keel, que es parte del contrato
 * público del evento: lo estampa {@code EventEnvelope.of(...)} al publicar y lo restaura
 * {@code CorrelationContext.runWith(metadata, ...)} al consumir. Va en el SOBRE y no solo en
 * las cabeceras nativas del broker por dos razones: con {@code reliability: outbox} la
 * publicación ocurre en otro hilo y otro instante —el contexto de la petición ya no existe, y lo
 * único que lo conserva es la fila—, y no todos los brokers tienen propagación nativa (SNS/SQS
 * no la tiene en Spring Cloud AWS). Donde sí la hay (Kafka, RabbitMQ), el consumo ya llega con la
 * traza abierta y aquí no se abre otra.
 *
 * <p>Estático porque lo usan dos piezas estáticas ({@code EventEnvelope.of} y
 * {@code CorrelationContext}); lo puebla {@code MessageTracingInstaller} al arrancar. Antes de
 * eso, o sin tracer, todo degrada a no hacer nada: nunca impide publicar ni consumir.
 */
public final class MessageTracing {

    /** Clave W3C del contexto de traza (https://www.w3.org/TR/trace-context/). */
    public static final String TRACEPARENT = "traceparent";

    /** Observación de la publicación de un evento del outbox. */
    public static final String OUTBOX_PUBLISH = "keel.outbox.publish";

    /** Observación del consumo de un mensaje con envoltura keel. */
    public static final String MESSAGE_CONSUME = "keel.message.consume";

    private static final JsonMapper JSON = JsonMapper.builder().configureForJackson2().build();

    private static volatile Tracer tracer;
    private static volatile Propagator propagator;
    private static volatile ObservationRegistry registry = ObservationRegistry.NOOP;

    private MessageTracing() {
        // Clase de utilidad.
    }

    static void install(Tracer tracer, Propagator propagator, ObservationRegistry registry) {
        MessageTracing.tracer = tracer;
        MessageTracing.propagator = propagator;
        MessageTracing.registry = registry == null ? ObservationRegistry.NOOP : registry;
    }

    /** @return el traceparent del span activo, o null si no hay ninguno (o no hay telemetría). */
    public static String currentTraceparent() {
        Tracer current = tracer;
        Propagator format = propagator;
        if (current == null || format == null) {
            return null;
        }
        Span span = current.currentSpan();
        if (span == null || span.isNoop()) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        format.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * El traceparent de una envoltura ya serializada (la fila del outbox guarda el JSON entero).
     * Un payload que no se deja leer pierde la traza, nunca el evento: devuelve null.
     */
    public static String traceparentOfEnvelope(String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = JSON.readTree(envelopeJson).path("metadata").path(TRACEPARENT);
            return value.isString() ? value.asString() : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    /**
     * Ejecuta la acción dentro de una observación hija del contexto remoto. Sin traceparent
     * abre una raíz (el evento se sigue viendo); si el span activo YA es de esa traza —llegó por
     * la cabecera nativa del broker— no abre otra.
     */
    public static void continueFrom(String traceparent, String name, Kind kind, String eventType, Runnable action) {
        if (traceparent != null && sameTraceAsCurrent(traceparent)) {
            action.run();
            return;
        }
        Map<String, String> carrier = traceparent == null ? Map.of() : Map.of(TRACEPARENT, traceparent);
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>((source, key) -> source.get(key), kind);
        context.setCarrier(carrier);
        String type = eventType == null || eventType.isBlank() ? "unknown" : eventType;
        Observation.createNotStarted(name, () -> context, registry)
                .contextualName(name + " " + type)
                .lowCardinalityKeyValue("keel.event.type", type)
                .observe(action);
    }

    private static boolean sameTraceAsCurrent(String traceparent) {
        Tracer current = tracer;
        if (current == null) {
            return false;
        }
        Span span = current.currentSpan();
        if (span == null || span.isNoop()) {
            return false;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 2 && parts[1].equals(span.context().traceId());
    }
}
