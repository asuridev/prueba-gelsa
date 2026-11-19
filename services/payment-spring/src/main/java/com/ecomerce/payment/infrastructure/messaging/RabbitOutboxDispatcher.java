package com.ecomerce.payment.infrastructure.messaging;

import com.ecomerce.payment.application.support.ContextPropagatingExecutors;
import com.ecomerce.payment.infrastructure.messaging.outbox.OutboxDispatcher;
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
 * Entrega al broker (RabbitMQ) una fila del outbox: el payload ya es la EventEnvelope serializada y
 * se manda tal cual. Espera el confirm del broker y lanza si no llega o si el mensaje volvió sin
 * entregar, para que el relay cuente el intento y reintente.
 */
@Component
public class RabbitOutboxDispatcher implements OutboxDispatcher {

    /** Plazo único del intento (envío + confirm), por encima del recovery-interval del contenedor de listeners. */
    private static final Duration DISPATCH_DEADLINE = Duration.ofSeconds(6);

    private final RabbitTemplate rabbitTemplate;
    private final long recoveryIntervalMillis;
    private final AtomicLong lastConnectionResetAt = new AtomicLong(0L);
    private final ExecutorService dispatchExecutor = ContextPropagatingExecutors.newVirtualThreadPerTaskExecutor();

    public RabbitOutboxDispatcher(RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.listener.recovery-interval-ms:5000}") long recoveryIntervalMillis) {
        this.rabbitTemplate = rabbitTemplate;
        this.recoveryIntervalMillis = recoveryIntervalMillis;
    }

    @Override
    public void dispatch(String destination, String routingKey, String eventType, String payload) {
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

    /** Descarta la conexión cacheada tras un fallo, salvo que ya se descartara hace menos de un recovery-interval. */
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
