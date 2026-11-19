package com.ecomerce.payment.infrastructure.messaging;

import com.ecomerce.payment.domain.events.PaymentActionRejectedEvent;
import com.ecomerce.payment.domain.events.PaymentActionRequiredEvent;
import com.ecomerce.payment.domain.events.PaymentAuthorizedEvent;
import com.ecomerce.payment.domain.events.PaymentCanceledEvent;
import com.ecomerce.payment.domain.events.PaymentCapturedEvent;
import com.ecomerce.payment.domain.events.PaymentFailedEvent;
import com.ecomerce.payment.domain.events.PaymentRefundedEvent;
import com.ecomerce.payment.infrastructure.correlation.CorrelationContext;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentActionRejectedIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentActionRequiredIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentAuthorizedIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentCanceledIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentCapturedIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentFailedIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.events.PaymentRefundedIntegrationEvent;
import com.ecomerce.payment.infrastructure.messaging.outbox.OutboxAppended;
import com.ecomerce.payment.infrastructure.messaging.outbox.OutboxEventJpa;
import com.ecomerce.payment.infrastructure.messaging.outbox.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * PaymentDomainEventBridge — traduce cada evento de dominio a su evento de
 * integración y lo entrega al outbox DENTRO de la transacción que provocó el cambio: la fila y el
 * cambio del agregado confirman o revierten juntos (reliability: outbox).
 *
 * Los eventos llegan aquí porque el adaptador de repositorio drena
 * pullDomainEvents() al persistir el agregado. Nadie más publica eventos.
 */
@Component
public class PaymentDomainEventBridge {

    @Value("${messaging.publishing.destination:payment.events}")
    private String destination;

    @Value("${messaging.publishing.routing-keys.payment-authorized:payment.payment-authorized}")
    private String paymentAuthorizedRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-action-required:payment.payment-action-required}")
    private String paymentActionRequiredRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-captured:payment.payment-captured}")
    private String paymentCapturedRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-failed:payment.payment-failed}")
    private String paymentFailedRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-canceled:payment.payment-canceled}")
    private String paymentCanceledRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-refunded:payment.payment-refunded}")
    private String paymentRefundedRoutingKey;

    @Value("${messaging.publishing.routing-keys.payment-action-rejected:payment.payment-action-rejected}")
    private String paymentActionRejectedRoutingKey;

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public PaymentDomainEventBridge(OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /** PaymentAuthorized: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentAuthorizedEvent(PaymentAuthorizedEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentAuthorizedIntegrationEvent integrationEvent = new PaymentAuthorizedIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.amount(), event.currency(), event.gatewayPaymentId());
        EventEnvelope<PaymentAuthorizedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentAuthorizedRoutingKey, "PaymentAuthorized", envelope);
    }

    /** PaymentActionRequired: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentActionRequiredEvent(PaymentActionRequiredEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentActionRequiredIntegrationEvent integrationEvent = new PaymentActionRequiredIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.amount(), event.currency(), event.customerAction());
        EventEnvelope<PaymentActionRequiredIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentActionRequiredRoutingKey, "PaymentActionRequired", envelope);
    }

    /** PaymentCaptured: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentCapturedEvent(PaymentCapturedEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentCapturedIntegrationEvent integrationEvent = new PaymentCapturedIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.amount(), event.currency());
        EventEnvelope<PaymentCapturedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentCapturedRoutingKey, "PaymentCaptured", envelope);
    }

    /** PaymentFailed: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentFailedEvent(PaymentFailedEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentFailedIntegrationEvent integrationEvent = new PaymentFailedIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.amount(), event.currency(), event.failureReason());
        EventEnvelope<PaymentFailedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentFailedRoutingKey, "PaymentFailed", envelope);
    }

    /** PaymentCanceled: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentCanceledEvent(PaymentCanceledEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentCanceledIntegrationEvent integrationEvent = new PaymentCanceledIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.amount(), event.currency(), event.requested());
        EventEnvelope<PaymentCanceledIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentCanceledRoutingKey, "PaymentCanceled", envelope);
    }

    /** PaymentRefunded: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentRefundedEvent(PaymentRefundedEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentRefundedIntegrationEvent integrationEvent = new PaymentRefundedIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.refundRequestId(), event.refundAmount(), event.refundedAmount(), event.currency(), event.status());
        EventEnvelope<PaymentRefundedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentRefundedRoutingKey, "PaymentRefunded", envelope);
    }

    /** PaymentActionRejected: evento de dominio → evento de integración. */
    @EventListener
    public void onPaymentActionRejectedEvent(PaymentActionRejectedEvent event) {
        String correlationId = CorrelationContext.get();
        PaymentActionRejectedIntegrationEvent integrationEvent = new PaymentActionRejectedIntegrationEvent(event.metadata(), event.paymentId(), event.chargeRequestId(), event.customerRef(), event.action(), event.refundRequestId(), event.refundAmount(), event.status());
        EventEnvelope<PaymentActionRejectedIntegrationEvent> envelope = EventEnvelope.of(event.metadata(), integrationEvent, correlationId);
        append(paymentActionRejectedRoutingKey, "PaymentActionRejected", envelope);
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
