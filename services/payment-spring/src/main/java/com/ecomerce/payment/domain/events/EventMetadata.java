package com.ecomerce.payment.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata de un evento de dominio: id único de esta ocurrencia (clave de
 * idempotencia), tipo lógico, versión del payload, instante en que ocurrió y
 * servicio de origen. La correlación y el contexto de traza los rellena la
 * infraestructura de publicación, que es quien conoce el contexto de la petición.
 *
 * <p>{@code traceparent} es el contexto de traza W3C del hecho
 * (https://www.w3.org/TR/trace-context/). Es parte del contrato del evento con o
 * sin telemetría en este servicio: sin ella viaja a null, y un consumidor que sí
 * la tenga la continúa cuando llega.
 */
public record EventMetadata(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String source,
        String correlationId,
        String traceparent) {

    /** Fábrica que usa el agregado al emitir: sin correlación ni traza, que son de infraestructura. */
    public static EventMetadata now(String eventType) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                "payment",
                null,
                null);
    }

    /** Copia con la correlación del request; conserva el eventId original. */
    public EventMetadata withCorrelationId(String correlationId) {
        return new EventMetadata(eventId, eventType, eventVersion, occurredAt, source, correlationId, traceparent);
    }

    /** Copia con la correlación y el contexto de traza del request; conserva el eventId original. */
    public EventMetadata withContext(String correlationId, String traceparent) {
        return new EventMetadata(eventId, eventType, eventVersion, occurredAt, source, correlationId, traceparent);
    }
}
