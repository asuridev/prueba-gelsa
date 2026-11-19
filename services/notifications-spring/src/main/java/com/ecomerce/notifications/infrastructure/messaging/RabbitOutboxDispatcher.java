package com.ecomerce.notifications.infrastructure.messaging;

import com.ecomerce.notifications.application.support.ContextPropagatingExecutors;
import com.ecomerce.notifications.infrastructure.messaging.outbox.OutboxDispatcher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Entrega al broker una fila del outbox. El payload ya es la {@code EventEnvelope} serializada: se manda tal cual.
 *
 * <p>Espera el confirm del broker y el aviso de «devuelto» (sin binding): sin ellos un nack o una routing key sin
 * cola se perderían con el outbox en verde. Lanza si la entrega no se confirma, y el relay reintenta.
 */
@Component
public class RabbitOutboxDispatcher implements OutboxDispatcher {

    /** Deadline del intento (send + confirm), por encima del recovery-interval del contenedor de listeners. */
    private static final Duration DISPATCH_DEADLINE = Duration.ofSeconds(6);

    private final RabbitTemplate rabbitTemplate;
    private final long recoveryIntervalMillis;
    private final AtomicLong lastConnectionResetAt = new AtomicLong(0L);
    // El executor del proyecto propaga el contexto (traza y correlación) al hilo del intento.
    private final ExecutorService dispatchExecutor = ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor();

    public RabbitOutboxDispatcher(RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.listener.recovery-interval-ms:5000}") long recoveryIntervalMillis) {
        this.rabbitTemplate = rabbitTemplate;
        this.recoveryIntervalMillis = recoveryIntervalMillis;
    }

    @Override
    public void dispatch(String destination, String routingKey, String eventType, String payload) {
        // En un hilo aparte que se abandona al agotar el plazo: una reconexión en curso puede dejar el send
        // bloqueado dentro del cliente AMQP sin lanzar.
        Future<Void> attempt = dispatchExecutor.submit(() -> {
            sendAndAwaitConfirm(destination, routingKey, eventType, payload);
            return null;
        });
        try {
            attempt.get(DISPATCH_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            attempt.cancel(true);
            resetConnectionAfterFailure();
            throw new AmqpException("Sin confirmación del broker para " + routingKey + " en " + DISPATCH_DEADLINE, timedOut);
        } catch (ExecutionException failed) {
            resetConnectionAfterFailure();
            Throwable cause = failed.getCause();
            throw cause instanceof AmqpException amqp ? amqp : new AmqpException("Fallo entregando " + routingKey, cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            attempt.cancel(true);
            resetConnectionAfterFailure();
            throw new AmqpException("Interrumpido esperando la confirmación de " + routingKey, interrupted);
        }
    }

    /** Descarta la conexión cacheada tras un fallo, pero no más de una vez por recovery-interval. */
    private void resetConnectionAfterFailure() {
        if (!(rabbitTemplate.getConnectionFactory() instanceof CachingConnectionFactory caching)) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastConnectionResetAt.get();
        if (now - previous < recoveryIntervalMillis) {
            return;
        }
        if (lastConnectionResetAt.compareAndSet(previous, now)) {
            caching.resetConnection();
        }
    }

    private void sendAndAwaitConfirm(String destination, String routingKey, String eventType, String payload) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setType(eventType);
        CorrelationData confirmation = new CorrelationData(eventType + ":" + routingKey);
        rabbitTemplate.send(destination, routingKey,
                MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8)).andProperties(props).build(),
                confirmation);
        try {
            CorrelationData.Confirm confirm = confirmation.getFuture().get(DISPATCH_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.ack()) {
                throw new AmqpException("El broker no confirmó la entrega en " + destination + " (" + routingKey + "): "
                        + (confirm == null ? "sin respuesta" : confirm.reason()));
            }
            // Un ack dice «lo recibí», no «lo entregué a alguien»: el returned distingue el exchange sin binding.
            if (confirmation.getReturned() != null) {
                throw new AmqpException("Publicado en " + destination + " sin binding para " + routingKey
                        + ": el mensaje volvió sin entregar");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrumpido esperando la confirmación de " + routingKey, interrupted);
        } catch (ExecutionException | TimeoutException notConfirmed) {
            throw new AmqpException("Sin confirmación del broker para " + routingKey, notConfirmed);
        }
    }
}
