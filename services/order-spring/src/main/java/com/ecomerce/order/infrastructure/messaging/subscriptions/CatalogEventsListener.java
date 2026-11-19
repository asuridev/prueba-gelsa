package com.ecomerce.order.infrastructure.messaging.subscriptions;

import com.ecomerce.order.application.commands.RemoveProductCommand;
import com.ecomerce.order.application.commands.UpsertProductCommand;
import com.ecomerce.order.application.interfaces.Command;
import com.ecomerce.order.domain.errors.DomainException;
import com.ecomerce.order.domain.errors.NotFoundException;
import com.ecomerce.order.domain.events.EventMetadata;
import com.ecomerce.order.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import com.ecomerce.order.infrastructure.messaging.idempotency.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Listener de la cola de catalog (catalog.events → order.catalog): UN solo listener para ProductUpserted y
 * ProductRetired, porque varios {@code @RabbitListener} sobre la misma cola competirían por los mensajes. Enruta
 * por {@code metadata.eventType} y descarta SIN lanzar lo que no es suyo.
 *
 * <p>Ninguna de las dos operaciones declara transiciones, así que la ventana del duplicado se cierra con
 * {@code tryRecord} ANTES de despachar. Como un fallo del handler dejaría entonces el mensaje marcado y perdido,
 * el conflicto de concurrencia que el diseño declara (dos altas simultáneas del mismo producto chocan en la
 * unicidad del id) se resuelve AQUÍ, reintentando el despacho, donde la regla de versión decide.
 */
@Component
public class CatalogEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventsListener.class);

    private static final String HANDLER_ID = "CatalogEventsListener";

    private static final int CONFLICT_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final UseCaseMediator mediator;
    private final IdempotencyGuard idempotencyGuard;

    public CatalogEventsListener(ObjectMapper objectMapper, UseCaseMediator mediator, IdempotencyGuard idempotencyGuard) {
        this.objectMapper = objectMapper;
        this.mediator = mediator;
        this.idempotencyGuard = idempotencyGuard;
    }

    @RabbitListener(queues = "${messaging.subscriptions.product-upserted.queue:order.catalog}")
    public void onMessage(Message raw) {
        JsonNode root = parse(raw);
        EventMetadata metadata = read(root.path("metadata"), EventMetadata.class);
        if (metadata == null || metadata.eventType() == null) {
            throw new AmqpRejectAndDontRequeueException("El mensaje de catalog no trae metadata.eventType");
        }
        JsonNode data = root.path("data");
        Command command = switch (metadata.eventType()) {
            case "ProductUpserted" -> {
                ProductUpsertedMessage message = read(data, ProductUpsertedMessage.class);
                message.requireContract();
                yield new UpsertProductCommand(message.productId(), message.sku(), message.name(), message.price(),
                        message.currency(), message.version());
            }
            case "ProductRetired" -> {
                ProductRetiredMessage message = read(data, ProductRetiredMessage.class);
                message.requireContract();
                yield new RemoveProductCommand(message.productId(), message.version());
            }
            // El canal lo comparten todos los eventos de catalog: lo ajeno se descarta sin lanzar.
            default -> null;
        };
        if (command == null) {
            return;
        }
        CorrelationContext.runWith(metadata, () -> consume(metadata.eventId(), command));
    }

    private void consume(String eventId, Command command) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Mensaje de catalog sin metadata.eventId: no hay clave con que deduplicar y se descarta");
            return;
        }
        if (!idempotencyGuard.tryRecord(HANDLER_ID, eventId)) {
            return;
        }
        try {
            dispatchResolvingConflicts(command);
        } catch (NotFoundException missing) {
            throw missing;
        } catch (DomainException rejected) {
            throw new AmqpRejectAndDontRequeueException(rejected.getMessage(), rejected);
        }
    }

    private void dispatchResolvingConflicts(Command command) {
        for (int attempt = 1; ; attempt++) {
            try {
                mediator.dispatch(command);
                return;
            } catch (DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
                // La otra escritura ya confirmó: el reintento relee y aplica la regla de versión.
                if (attempt >= CONFLICT_ATTEMPTS) {
                    throw conflict;
                }
            }
        }
    }

    private JsonNode parse(Message raw) {
        try {
            return objectMapper.readTree(raw.getBody());
        } catch (RuntimeException unreadable) {
            throw new AmqpRejectAndDontRequeueException("El cuerpo del mensaje de catalog no es JSON", unreadable);
        }
    }

    private <T> T read(JsonNode node, Class<T> type) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            if (type == EventMetadata.class) {
                return null;
            }
            throw new IllegalArgumentException("El mensaje no trae data: " + type.getSimpleName());
        }
        try {
            return objectMapper.treeToValue(node, type);
        } catch (RuntimeException unreadable) {
            throw new AmqpRejectAndDontRequeueException("El mensaje de catalog no cumple su contrato: " + type.getSimpleName(), unreadable);
        }
    }
}
