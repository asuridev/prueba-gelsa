package com.ecomerce.order.infrastructure.messaging.events;

import com.ecomerce.order.domain.events.EventMetadata;
import com.ecomerce.order.domain.valueobject.NotificationVariable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * Evento de integración NotificationRequested: proyección de wire del evento de dominio
 * NotificationRequestedEvent, publicado en el canal 'notificationRequests'.
 *
 * Deliberadamente desacoplado del dominio: cambiar el broker o el formato de
 * serialización no debe alcanzar a domain/events.
 *
 * La metadata se conserva como componente (el bridge la necesita para construir
 * la EventEnvelope) pero NO se serializa: la metadata autoritativa del mensaje
 * es la del envelope, y duplicarla en 'data' confundiría al consumidor.
 */
public record NotificationRequestedIntegrationEvent(@JsonIgnore EventMetadata metadata, String idempotencyKey, String templateCode, List<String> recipients, List<NotificationVariable> variables) {
}
