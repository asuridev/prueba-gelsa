package com.ecomerce.notifications.infrastructure.messaging;

import com.ecomerce.notifications.domain.events.EventMetadata;
import com.ecomerce.notifications.infrastructure.telemetry.MessageTracing;

/**
 * Envoltura estándar de los eventos publicados: metadata + payload.
 *
 * La metadata es la MISMA que el agregado estampó al emitir el evento de
 * dominio (ver domain/events/EventMetadata): conserva el eventId, que es la
 * clave de idempotencia del consumidor. Aquí solo se le añaden la correlación
 * y el contexto de traza del request, que el dominio no conoce.
 */
public record EventEnvelope<T>(EventMetadata metadata, T data) {

    public static <T> EventEnvelope<T> of(EventMetadata metadata, T data, String correlationId) {
        return new EventEnvelope<>(metadata.withContext(correlationId, MessageTracing.currentTraceparent()), data);
    }
}
