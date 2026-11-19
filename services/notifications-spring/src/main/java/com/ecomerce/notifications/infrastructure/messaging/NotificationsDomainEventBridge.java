package com.ecomerce.notifications.infrastructure.messaging;

import com.ecomerce.notifications.domain.events.EmailDeliveryFailedEvent;
import com.ecomerce.notifications.domain.events.EmailSentEvent;
import com.ecomerce.notifications.infrastructure.correlation.CorrelationContext;
import com.ecomerce.notifications.infrastructure.messaging.events.EmailDeliveryFailedIntegrationEvent;
import com.ecomerce.notifications.infrastructure.messaging.events.EmailSentIntegrationEvent;
import com.ecomerce.notifications.infrastructure.messaging.outbox.OutboxAppended;
import com.ecomerce.notifications.infrastructure.messaging.outbox.OutboxEventJpa;
import com.ecomerce.notifications.infrastructure.messaging.outbox.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * NotificationsDomainEventBridge — traduce cada evento de dominio a su evento de
 * integración y lo entrega al outbox DENTRO de la transacción que provocó el cambio: la fila y el
 * cambio del agregado confirman o revierten juntos (reliability: outbox).
 *
 * Los eventos llegan aquí porque el adaptador de repositorio drena
 * pullDomainEvents() al persistir el agregado. Nadie más publica eventos.
 */
@Component
public class NotificationsDomainEventBridge {

    @Value("${messaging.publishing.destination:notifications.events}")
    private String destination;

    @Value("${messaging.publishing.routing-keys.email-sent:notifications.email-sent}")
    private String emailSentRoutingKey;

    @Value("${messaging.publishing.routing-keys.email-delivery-failed:notifications.email-delivery-failed}")
    private String emailDeliveryFailedRoutingKey;

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public NotificationsDomainEventBridge(OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /** EmailSent: evento de dominio → evento de integración. */
    @EventListener
    public void onEmailSentEvent(EmailSentEvent event) {
        String correlationId = CorrelationContext.get();
        EmailSentIntegrationEvent integrationEvent = new EmailSentIntegrationEvent(event.metadata(), event.messageId(), event.requestedBy(), event.idempotencyKey(), event.templateCode(), event.templateVersionNumber(), event.sentAt());
        EventEnvelope<EmailSentIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(emailSentRoutingKey, "EmailSent", envelope);
    }

    /** EmailDeliveryFailed: evento de dominio → evento de integración. */
    @EventListener
    public void onEmailDeliveryFailedEvent(EmailDeliveryFailedEvent event) {
        String correlationId = CorrelationContext.get();
        EmailDeliveryFailedIntegrationEvent integrationEvent = new EmailDeliveryFailedIntegrationEvent(event.metadata(), event.messageId(), event.requestedBy(), event.idempotencyKey(), event.templateCode(), event.templateVersionNumber(), event.failureReason(), event.failedAt());
        EventEnvelope<EmailDeliveryFailedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(emailDeliveryFailedRoutingKey, "EmailDeliveryFailed", envelope);
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
