package com.ecomerce.notifications.infrastructure.messaging.outbox;

import java.util.UUID;

/**
 * Lo que el relay necesita de una fila reclamada del outbox para publicarla.
 *
 * <p>Un record y no la entidad JPA a propósito: la entidad queda detached en cuanto la
 * transacción de reclamo confirma —que es justo antes de publicar—, así que leerla ahí
 * depende de qué tuviera cargado Hibernate. Esto no depende de nada.
 */
record ClaimedOutboxEvent(UUID id, String destination, String routingKey, String eventType, String payload) {
}
