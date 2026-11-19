package com.ecomerce.payment.infrastructure.configurations.broker;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Topología de publicación del servicio: el exchange de eventos y una cola durable por canal del
 * diseño (paymentEvents), nombrada como el canal y con un binding por cada routing key de sus
 * eventos. Sin la cola, el exchange descartaría en silencio todo lo publicado.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange domainEventsExchange(@Value("${messaging.publishing.destination:payment.events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Declarables publishedChannels(
            TopicExchange domainEventsExchange,
            @Value("${messaging.publishing.routing-keys.payment-authorized:payment.payment-authorized}") String authorized,
            @Value("${messaging.publishing.routing-keys.payment-action-required:payment.payment-action-required}") String actionRequired,
            @Value("${messaging.publishing.routing-keys.payment-captured:payment.payment-captured}") String captured,
            @Value("${messaging.publishing.routing-keys.payment-failed:payment.payment-failed}") String failed,
            @Value("${messaging.publishing.routing-keys.payment-canceled:payment.payment-canceled}") String canceled,
            @Value("${messaging.publishing.routing-keys.payment-refunded:payment.payment-refunded}") String refunded,
            @Value("${messaging.publishing.routing-keys.payment-action-rejected:payment.payment-action-rejected}") String actionRejected) {
        Queue paymentEvents = QueueBuilder.durable("paymentEvents").build();
        List<Binding> bindings = List.of(
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(authorized),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(actionRequired),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(captured),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(failed),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(canceled),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(refunded),
                BindingBuilder.bind(paymentEvents).to(domainEventsExchange).with(actionRejected));
        List<Declarable> all = new java.util.ArrayList<>();
        all.add(paymentEvents);
        all.addAll(bindings);
        return new Declarables(all);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    /** Siempre vía el configurer de Boot: así llegan al contenedor las propiedades spring.rabbitmq.listener.*. */
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
