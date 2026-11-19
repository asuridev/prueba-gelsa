package com.ecomerce.notifications.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * El relay aceptó el correo de un mensaje. No implica que llegara al buzón.
 */
public record EmailSentEvent(EventMetadata metadata, UUID messageId, String requestedBy, String idempotencyKey, String templateCode, Integer templateVersionNumber, Instant sentAt) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static EmailSentEvent of(UUID messageId, String requestedBy, String idempotencyKey, String templateCode, Integer templateVersionNumber, Instant sentAt) {
        return new EmailSentEvent(EventMetadata.now("EmailSent"), messageId, requestedBy, idempotencyKey, templateCode, templateVersionNumber, sentAt);
    }
}
