package com.ecomerce.notifications.domain.events;

import com.ecomerce.notifications.domain.enums.FailureReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Un mensaje acabó en failed y no se reintentará. failureReason distingue el rechazo del relay, el error sin respuesta, el rescate de un mensaje atascado en sending y la supresión de un destinatario descubierta al enviar.
 */
public record EmailDeliveryFailedEvent(EventMetadata metadata, UUID messageId, String requestedBy, String idempotencyKey, String templateCode, Integer templateVersionNumber, FailureReason failureReason, Instant failedAt) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static EmailDeliveryFailedEvent of(UUID messageId, String requestedBy, String idempotencyKey, String templateCode, Integer templateVersionNumber, FailureReason failureReason, Instant failedAt) {
        return new EmailDeliveryFailedEvent(EventMetadata.now("EmailDeliveryFailed"), messageId, requestedBy, idempotencyKey, templateCode, templateVersionNumber, failureReason, failedAt);
    }
}
