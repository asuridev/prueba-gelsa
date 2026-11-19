package com.ecomerce.notifications.infrastructure.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Broker RabbitMQ: el exchange de eventos del servicio, la cola del canal publicado (notificationEvents) y el
 * conversor JSON. La topología de la suscripción (cola, descarte, reintento) es de {@link RabbitTopologyConfig}.
 *
 * <p>La cola del canal no es opcional: un exchange topic sin bindings descarta en silencio lo que se publica.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange domainEventsExchange(@Value("${messaging.publishing.destination:notifications.events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    /** Una cola durable por canal publicado, con binding a las routing keys de sus eventos. */
    @Bean
    public Declarables publishedChannels(TopicExchange domainEventsExchange,
            @Value("${messaging.publishing.routing-keys.email-sent:notifications.email-sent}") String emailSentKey,
            @Value("${messaging.publishing.routing-keys.email-delivery-failed:notifications.email-delivery-failed}") String emailDeliveryFailedKey) {
        Queue notificationEvents = QueueBuilder.durable("notificationEvents").build();
        return new Declarables(
                notificationEvents,
                BindingBuilder.bind(notificationEvents).to(domainEventsExchange).with(emailSentKey),
                BindingBuilder.bind(notificationEvents).to(domainEventsExchange).with(emailDeliveryFailedKey));
    }

    // Jackson 3 (Spring AMQP 4): el convertidor recibe el JsonMapper de la aplicación (con TimestampModule).
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Siempre vía el configurer de Boot: un factory cableado a mano ignoraría spring.rabbitmq.listener.simple.*
     * (retry incluido). El recovery-interval se fija aquí porque RabbitProperties no lo expone; es la misma clave
     * de la que el dispatcher del outbox deriva su deadline.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${rabbitmq.listener.recovery-interval-ms:5000}") long recoveryIntervalMillis) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setRecoveryInterval(recoveryIntervalMillis);
        return factory;
    }
}
