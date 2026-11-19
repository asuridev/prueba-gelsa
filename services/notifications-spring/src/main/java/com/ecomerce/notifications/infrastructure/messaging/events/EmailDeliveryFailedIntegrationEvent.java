package com.ecomerce.notifications.infrastructure.messaging.events;

import com.ecomerce.notifications.domain.enums.FailureReason;
import com.ecomerce.notifications.domain.events.EventMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de integración EmailDeliveryFailed: proyección de wire del evento de dominio
 * EmailDeliveryFailedEvent, publicado en el canal 'notificationEvents'.
 *
 * Deliberadamente desacoplado del dominio: cambiar el broker o el formato de
 * serialización no debe alcanzar a domain/events.
 *
 * La metadata se conserva como componente (el bridge la necesita para construir
 * la EventEnvelope) pero NO se serializa: la metadata autoritativa del mensaje
 * es la del envelope, y duplicarla en 'data' confundiría al consumidor.
 */
public record EmailDeliveryFailedIntegrationEvent(@JsonIgnore EventMetadata metadata, UUID messageId, String requestedBy, String idempotencyKey, String templateCode, Integer templateVersionNumber, FailureReason failureReason, Instant failedAt) {
}
