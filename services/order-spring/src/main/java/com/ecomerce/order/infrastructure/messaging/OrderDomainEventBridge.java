package com.ecomerce.order.infrastructure.messaging;

import com.ecomerce.order.domain.events.NotificationRequestedEvent;
import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import com.ecomerce.order.infrastructure.messaging.events.NotificationRequestedIntegrationEvent;
import com.ecomerce.order.infrastructure.messaging.outbox.OutboxAppended;
import com.ecomerce.order.infrastructure.messaging.outbox.OutboxEventJpa;
import com.ecomerce.order.infrastructure.messaging.outbox.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * OrderDomainEventBridge — traduce cada evento de dominio a su evento de
 * integración y lo entrega al outbox DENTRO de la transacción que provocó el cambio: la fila y el
 * cambio del agregado confirman o revierten juntos (reliability: outbox).
 *
 * Los eventos llegan aquí porque el adaptador de repositorio drena
 * pullDomainEvents() al persistir el agregado. Nadie más publica eventos.
 */
@Component
public class OrderDomainEventBridge {

    @Value("${messaging.publishing.destination:order.events}")
    private String destination;

    @Value("${messaging.publishing.routing-keys.notification-requested:order.notification-requested}")
    private String notificationRequestedRoutingKey;

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public OrderDomainEventBridge(OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /** NotificationRequested: evento de dominio → evento de integración. */
    @EventListener
    public void onNotificationRequestedEvent(NotificationRequestedEvent event) {
        String correlationId = CorrelationContext.get();
        NotificationRequestedIntegrationEvent integrationEvent = new NotificationRequestedIntegrationEvent(event.metadata(), event.idempotencyKey(), event.templateCode(), event.recipients(), event.variables());
        EventEnvelope<NotificationRequestedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(notificationRequestedRoutingKey, "NotificationRequested", envelope);
    }

    private void append(String routingKey, String eventType, EventEnvelope<?> envelope) {
        try {
            outboxRepository.save(new OutboxEventJpa(
                    UUID.randomUUID(),
                    destination,
                    routingKey,
                    eventType,
                    objectMapper.writeValueAsString(envelope),
                    Instant.now(),
                    null,
                    0,
                    null,
                    null));
            // Despierta al relay de ESTA réplica tras el commit (DSL 2.26, inc. 9): el evento sale ya, sin esperar
            // al sondeo de respaldo. Si la transacción revierte, el aviso no llega a nadie.
            events.publishEvent(OutboxAppended.INSTANCE);
        } catch (JacksonException ex) {
            // Serializar un evento propio no puede fallar: si falla, el diseño
            // del payload está roto y la transacción debe revertir.
            throw new IllegalStateException("No se pudo serializar el evento " + eventType, ex);
        }
    }
}
