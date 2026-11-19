package com.ecomerce.order.infrastructure.messaging.subscriptions;

import com.ecomerce.order.application.commands.ApplyPaymentActionRejectedCommand;
import com.ecomerce.order.application.commands.ApplyPaymentActionRequiredCommand;
import com.ecomerce.order.application.commands.ApplyPaymentAuthorizedCommand;
import com.ecomerce.order.application.commands.ApplyPaymentCanceledCommand;
import com.ecomerce.order.application.commands.ApplyPaymentCapturedCommand;
import com.ecomerce.order.application.commands.ApplyPaymentFailedCommand;
import com.ecomerce.order.application.interfaces.Command;
import com.ecomerce.order.domain.enums.ApplyPaymentActionRejectedAction;
import com.ecomerce.order.domain.errors.DomainException;
import com.ecomerce.order.domain.errors.InvalidStateTransitionException;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Listener de la cola de payment (payment.events → order.payment): UN solo listener para los seis desenlaces del
 * cobro, porque varios {@code @RabbitListener} sobre la misma cola serían consumidores que compiten y perderían
 * mensajes. Enruta por {@code metadata.eventType} y descarta SIN lanzar lo que no es suyo.
 *
 * <p>Cada operación declara transiciones, así que el orden es {@code alreadyProcessed} antes de despachar y
 * {@code record} DESPUÉS de que el handler termine bien: un fallo transitorio deja el mensaje sin marcar y el
 * broker lo reentrega, y lo que frena la repetición es el agregado.
 */
@Component
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private static final String HANDLER_ID = "PaymentEventsListener";

    /** Intentos del despacho cuando otra escritura del mismo pedido gana el commit (la carrera se resuelve releyendo). */
    private static final int RACE_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final UseCaseMediator mediator;
    private final IdempotencyGuard idempotencyGuard;

    public PaymentEventsListener(ObjectMapper objectMapper, UseCaseMediator mediator, IdempotencyGuard idempotencyGuard) {
        this.objectMapper = objectMapper;
        this.mediator = mediator;
        this.idempotencyGuard = idempotencyGuard;
    }

    @RabbitListener(queues = "${messaging.subscriptions.payment-authorized.queue:order.payment}")
    public void onMessage(Message raw) {
        JsonNode root = parse(raw);
        EventMetadata metadata = read(root.path("metadata"), EventMetadata.class);
        if (metadata == null || metadata.eventType() == null) {
            // Sin envoltura Keel no hay tipo ni clave con que deduplicar: es un cuerpo roto.
            throw new AmqpRejectAndDontRequeueException("El mensaje de payment no trae metadata.eventType");
        }
        JsonNode data = root.path("data");
        Command command = switch (metadata.eventType()) {
            case "PaymentAuthorized" -> {
                PaymentAuthorizedMessage message = read(data, PaymentAuthorizedMessage.class);
                message.requireContract();
                yield new ApplyPaymentAuthorizedCommand(message.chargeRequestId(), message.paymentId());
            }
            case "PaymentActionRequired" -> {
                PaymentActionRequiredMessage message = read(data, PaymentActionRequiredMessage.class);
                message.requireContract();
                yield new ApplyPaymentActionRequiredCommand(message.chargeRequestId(), message.paymentId(), message.customerAction());
            }
            case "PaymentCaptured" -> {
                PaymentCapturedMessage message = read(data, PaymentCapturedMessage.class);
                message.requireContract();
                yield new ApplyPaymentCapturedCommand(message.chargeRequestId());
            }
            case "PaymentFailed" -> {
                PaymentFailedMessage message = read(data, PaymentFailedMessage.class);
                message.requireContract();
                yield new ApplyPaymentFailedCommand(message.chargeRequestId(), message.paymentId(), message.failureReason());
            }
            case "PaymentCanceled" -> {
                PaymentCanceledMessage message = read(data, PaymentCanceledMessage.class);
                message.requireContract();
                yield new ApplyPaymentCanceledCommand(message.chargeRequestId(), message.paymentId());
            }
            case "PaymentActionRejected" -> {
                PaymentActionRejectedMessage message = read(data, PaymentActionRejectedMessage.class);
                message.requireContract();
                yield new ApplyPaymentActionRejectedCommand(message.chargeRequestId(),
                        ApplyPaymentActionRejectedAction.valueOf(message.action().name()), message.status());
            }
            // El canal lo comparten todos los eventos de payment: lo ajeno se descarta sin lanzar.
            default -> null;
        };
        if (command == null) {
            return;
        }
        CorrelationContext.runWith(metadata, () -> consume(metadata.eventId(), command));
    }

    private void consume(String eventId, Command command) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Mensaje de payment sin metadata.eventId: no hay clave con que deduplicar y se descarta");
            return;
        }
        if (idempotencyGuard.alreadyProcessed(HANDLER_ID, eventId)) {
            return;
        }
        try {
            dispatchResolvingRaces(command);
        } catch (InvalidStateTransitionException resolvedRace) {
            // Otro camino ya movió el estado: es la carrera resuelta, no un fallo. El mensaje quedó atendido.
            log.debug("Desenlace de payment sin efecto: otro camino movió antes el pedido");
        } catch (NotFoundException missing) {
            // La carrera de orden de un consumo asíncrono: se reintenta (y, agotado, va al descarte).
            throw missing;
        } catch (DomainException rejected) {
            // Un rechazo de negocio no se reintenta: va al descarte, que es donde lo ve el emisor.
            throw new AmqpRejectAndDontRequeueException(rejected.getMessage(), rejected);
        }
        idempotencyGuard.record(HANDLER_ID, eventId);
    }

    private void dispatchResolvingRaces(Command command) {
        for (int attempt = 1; ; attempt++) {
            try {
                mediator.dispatch(command);
                return;
            } catch (OptimisticLockingFailureException lostCommit) {
                // Se escribió el mismo pedido a la vez: el reintento relee y decide con el estado ya confirmado.
                if (attempt >= RACE_ATTEMPTS) {
                    throw lostCommit;
                }
            }
        }
    }

    private JsonNode parse(Message raw) {
        try {
            return objectMapper.readTree(raw.getBody());
        } catch (RuntimeException unreadable) {
            throw new AmqpRejectAndDontRequeueException("El cuerpo del mensaje de payment no es JSON", unreadable);
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
            throw new AmqpRejectAndDontRequeueException("El mensaje de payment no cumple su contrato: " + type.getSimpleName(), unreadable);
        }
    }
}
