package com.ecomerce.notifications.infrastructure.messaging;

import com.ecomerce.notifications.application.commands.RequestNotificationCommand;
import com.ecomerce.notifications.application.interfaces.EntryGate;
import com.ecomerce.notifications.domain.errors.DomainException;
import com.ecomerce.notifications.domain.events.EventMetadata;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.correlation.CorrelationContext;
import com.ecomerce.notifications.infrastructure.messaging.idempotency.IdempotencyGuard;
import com.ecomerce.notifications.infrastructure.messaging.subscriptions.NotificationRequestedMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Suscripción NotificationRequested: la puerta de eventos de requestNotification, hermana de POST /notifications.
 *
 * <p>La cola es compartida con otros eventos del emisor, así que se filtra por {@code metadata.eventType} y lo ajeno
 * se descarta SIN lanzar. La identidad del llamante sale de {@code metadata.source} y solo resuelve si es un sistema
 * registrado con el scope notification:send; si no, va a la deadLetter. La deduplicación es
 * {@code alreadyProcessed} antes y {@code record} después de que el caso de uso termine bien: la guarda de verdad es
 * la clave natural (requestedBy, idempotencyKey), permanente y común a las dos puertas.
 */
@Component
public class NotificationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

    private static final String HANDLER_ID = "NotificationRequestedListener";
    private static final String EVENT_TYPE = "NotificationRequested";

    private final JsonMapper jsonMapper;
    private final UseCaseMediator mediator;
    private final IdempotencyGuard idempotencyGuard;
    private final Set<String> registeredSources;

    public NotificationRequestedListener(JsonMapper jsonMapper, UseCaseMediator mediator, IdempotencyGuard idempotencyGuard,
            @Value("${messaging.subscriptions.notification-requested.registered-sources:billing,shipping,dispatch-only}") String registeredSources) {
        this.jsonMapper = jsonMapper;
        this.mediator = mediator;
        this.idempotencyGuard = idempotencyGuard;
        this.registeredSources = Arrays.stream(registeredSources.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @RabbitListener(queues = "${messaging.subscriptions.notification-requested.queue:notifications.any-registered-system}")
    public void onMessage(Message amqpMessage) {
        JsonNode envelope;
        EventMetadata metadata;
        try {
            envelope = jsonMapper.readTree(amqpMessage.getBody());
            JsonNode metadataNode = envelope.get("metadata");
            if (metadataNode == null || metadataNode.isNull()) {
                throw new IllegalArgumentException("NotificationRequested: el mensaje no trae 'metadata'");
            }
            metadata = jsonMapper.treeToValue(metadataNode, EventMetadata.class);
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new AmqpRejectAndDontRequeueException("Mensaje ilegible o sin envoltura Keel", malformed);
        }
        // El canal transporta todos los eventos del emisor: lo ajeno se descarta sin excepción.
        if (!EVENT_TYPE.equals(metadata.eventType())) {
            return;
        }
        String eventId = metadata.eventId();
        // Primero la reentrega: un duplicado se confirma sin despachar.
        if (eventId != null && idempotencyGuard.alreadyProcessed(HANDLER_ID, eventId)) {
            return;
        }
        NotificationRequestedMessage data;
        try {
            JsonNode dataNode = envelope.get("data");
            if (dataNode == null || dataNode.isNull()) {
                throw new IllegalArgumentException("NotificationRequested: el mensaje no trae 'data'");
            }
            data = jsonMapper.treeToValue(dataNode, NotificationRequestedMessage.class);
            data.requireContract();
        } catch (JacksonException | IllegalArgumentException contract) {
            throw new AmqpRejectAndDontRequeueException("NotificationRequested incumple su contrato", contract);
        }
        // La identidad es la del emisor (metadata.source), ya resuelta; no se lee del payload.
        String source = metadata.source();
        if (source == null || !registeredSources.contains(source)) {
            log.atWarn().addKeyValue("keel.event_id", eventId)
                    .log("NotificationRequested de un emisor no registrado: va a la deadLetter");
            throw new AmqpRejectAndDontRequeueException("El emisor no corresponde a ningún sistema llamante registrado");
        }
        try {
            CorrelationContext.runWith(metadata, () -> mediator.dispatch(new RequestNotificationCommand(
                    source,
                    data.idempotencyKey(),
                    data.templateCode(),
                    data.recipients(),
                    data.variables() == null ? List.of() : data.variables(),
                    EntryGate.SUBSCRIPTION)));
        } catch (DomainException rejection) {
            // Un rechazo de negocio es permanente: a la deadLetter, sin gastar reintentos ni registrar el evento.
            throw new AmqpRejectAndDontRequeueException(rejection.getMessage(), rejection);
        }
        if (eventId != null) {
            idempotencyGuard.record(HANDLER_ID, eventId);
        }
    }
}
