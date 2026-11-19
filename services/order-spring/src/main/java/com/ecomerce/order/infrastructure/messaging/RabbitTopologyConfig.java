package com.ecomerce.order.infrastructure.messaging;

import com.ecomerce.order.domain.errors.DomainException;
import com.ecomerce.order.domain.errors.NotFoundException;
import java.util.Set;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de las suscripciones: PaymentAuthorized → order.payment, PaymentActionRequired → order.payment, PaymentCaptured → order.payment, PaymentFailed → order.payment, PaymentCanceled → order.payment, PaymentActionRejected → order.payment, ProductUpserted → order.catalog, ProductRetired → order.catalog.
 *
 * <p>El canal que nombra el diseño es el <b>exchange</b> del emisor, y de un exchange no se
 * consume: cuelga de él una cola propia de este servicio. Dos servicios suscritos al mismo
 * canal necesitan colas distintas, o el broker les repartiría los mensajes en vez de
 * entregárselos a ambos.
 *
 * <p>Estas declaraciones son de <b>build</b>: no las redeclares en el listener ni en otra
 * {@code @Configuration}. Dos declaraciones de la misma cola con argumentos distintos hacen
 * que RabbitMQ rechace la segunda con PRECONDITION_FAILED, y el contenedor no arranca. El
 * nombre de la cola está en {@code messaging.subscriptions.<evento>.queue}: léelo de ahí.
 */
@Configuration
public class RabbitTopologyConfig {

    /**
     * Topología de consumo de PaymentAuthorized, PaymentActionRequired, PaymentCaptured, PaymentFailed, PaymentCanceled, PaymentActionRejected, con su descarte enlazado por argumentos.
     *
     * <p>El binding usa {@code "#"} porque el canal de origen transporta todo lo que publica
     * su emisor, no solo lo que este servicio consume: el filtro por {@code eventType} va en
     * el listener, que descarta sin lanzar lo que no le corresponde.
     */
    @Bean
    public Declarables orderPaymentTopology() {
        TopicExchange source = new TopicExchange("payment.events", true, false);
        Queue queue = QueueBuilder.durable("order.payment")
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "order.payment-dlq")
                .build();
        Queue deadLetter = QueueBuilder.durable("order.payment-dlq").build();
        return new Declarables(source, queue, deadLetter, BindingBuilder.bind(queue).to(source).with("#"));
    }

    /**
     * Topología de consumo de ProductUpserted, ProductRetired, con su descarte enlazado por argumentos.
     *
     * <p>El binding usa {@code "#"} porque el canal de origen transporta todo lo que publica
     * su emisor, no solo lo que este servicio consume: el filtro por {@code eventType} va en
     * el listener, que descarta sin lanzar lo que no le corresponde.
     */
    @Bean
    public Declarables orderCatalogTopology() {
        TopicExchange source = new TopicExchange("catalog.events", true, false);
        Queue queue = QueueBuilder.durable("order.catalog")
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "order.catalog-dlq")
                .build();
        Queue deadLetter = QueueBuilder.durable("order.catalog-dlq").build();
        return new Declarables(source, queue, deadLetter, BindingBuilder.bind(queue).to(source).with("#"));
    }

    /**
     * Lo que NO se reintenta: el rechazo de negocio. El reintento de
     * {@code spring.rabbitmq.listener.simple.retry} (en {@code parameters/}, derivado de
     * {@code onFailure.retry} de PaymentAuthorized, PaymentActionRequired, PaymentCaptured, PaymentFailed, PaymentCanceled, PaymentActionRejected, ProductUpserted, ProductRetired) trata por defecto CUALQUIER
     * excepción como transitoria, y un {@code DomainException} no se resuelve mejor dentro de un
     * segundo. Peor: el reintento en memoria reinvoca el listener dentro de la MISMA entrega, así
     * que un guard que reclama antes de despachar ({@code tryRecord}) encuentra el evento ya
     * marcado, retorna sin lanzar y el contenedor confirma — el mensaje nunca llega al descarte.
     *
     * <p>Se miran las dos formas del rechazo: la excepción de dominio tal cual y la
     * {@code AmqpRejectAndDontRequeueException} con la que el listener la envuelve, en toda la
     * cadena de causas, porque el contenedor lo entrega todo dentro de un
     * {@code ListenerExecutionFailedException}. Agotado (o no reintentable), el recuperador de Boot
     * rechaza sin reencolar y la cola lo lleva a su DLQ si la declara.
     *
     * <p>La excepción es el «no existe» ({@code NotFoundException}) en las colas de
     * {@code RETRIES_NOT_FOUND}: allí suele ser la carrera de orden de un consumo asíncrono, y
     * el reintento la resuelve. Fuera de ellas, se trata como cualquier otro rechazo.
     *
     * <p>Solo decide QUÉ se reintenta: cuántas veces ({@code max-retries}) y con qué espera siguen
     * saliendo de las propiedades. Solo toca el reintento del LISTENER, no el del
     * {@code RabbitTemplate}. (En Boot 3 era un {@code RabbitRetryTemplateCustomizer} con un
     * {@code SimpleRetryPolicy} de Spring Retry; Spring AMQP 4 reintenta con el
     * {@code RetryPolicy} de Spring Framework, y la decisión es la misma.)
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer businessRejectionIsNotRetried() {
        return settings -> settings.setExceptionPredicate(RabbitTopologyConfig::isRetryable);
    }

    /**
     * ¿Se reintenta este fallo? El «no existe» se decide antes que el rechazo de negocio, y por
     * COLA: se reintenta solo en RETRIES_NOT_FOUND, vaya o no envuelto en el
     * AmqpRejectAndDontRequeueException.
     */
    static boolean isRetryable(Throwable failure) {
        if (isMissingTarget(failure)) {
            String queue = consumerQueue(failure);
            return queue != null && RETRIES_NOT_FOUND.contains(queue);
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof DomainException
                    || cause instanceof IllegalArgumentException
                    || cause instanceof AmqpRejectAndDontRequeueException) {
                return false;
            }
        }
        return true;
    }

    /** La cola de la que se consumió el mensaje que falló, o null si el fallo no la trae. */
    private static String consumerQueue(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ListenerExecutionFailedException listener && listener.getFailedMessage() != null) {
                return listener.getFailedMessage().getMessageProperties().getConsumerQueue();
            }
        }
        return null;
    }

    /**
     * Destinos en los que el «no existe» SÍ se reintenta. Es la excepción a no reintentar un rechazo de
     * negocio: en consumo asíncrono suele ser la carrera de orden —la compensación que llega antes que el
     * hecho que compensa—, y el reintento es lo que la resuelve; agotado, va al descarte igual. Solo
     * entran los destinos cuyas suscripciones tienen una guarda de dominio y un retry declarado: con
     * tryRecord el primer intento ya marcó el evento, y reintentar lo confirmaría sin efecto.
     */
    private static final Set<String> RETRIES_NOT_FOUND = Set.of("order.payment");

    /** ¿Hay un «no existe» del dominio en la cadena de causas? El contenedor lo entrega envuelto. */
    static boolean isMissingTarget(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof NotFoundException) {
                return true;
            }
        }
        return false;
    }
}
