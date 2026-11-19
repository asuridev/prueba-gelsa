package com.ecomerce.order.infrastructure.configurations.broker;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del broker RabbitMQ: la topología de PUBLICACIÓN y el contenedor de listeners.
 *
 * <p>La topología de las suscripciones (colas de consumo, bindings y descartes) es de build y vive en
 * {@code RabbitTopologyConfig}: no se redeclara aquí.
 *
 * <p>Los listeners reciben el {@code Message} crudo y parsean la envoltura Keel ellos mismos (una cola
 * compartida obliga a enrutar por {@code metadata.eventType}), así que no hace falta un conversor JSON.
 */
@Configuration
public class RabbitMqConfig {

    /**
     * Exchange de eventos del servicio y una cola durable por canal publicado, con el nombre del canal
     * ({@code notificationRequests}) y un binding a la routing key de su evento. Sin binding, un exchange
     * topic descarta en silencio lo que se publica (y con {@code mandatory} el dispatcher lo detecta como
     * returned). En despliegue, el dueño del canal (notifications) puede colgar además su propia cola.
     */
    @Bean
    public Declarables publishedChannels(
            @Value("${messaging.publishing.destination:order.events}") String destination,
            @Value("${messaging.publishing.routing-keys.notification-requested:order.notification-requested}") String notificationRequestedKey) {
        TopicExchange exchange = new TopicExchange(destination, true, false);
        Queue notificationRequests = QueueBuilder.durable("notificationRequests").build();
        return new Declarables(exchange, notificationRequests,
                BindingBuilder.bind(notificationRequests).to(exchange).with(notificationRequestedKey));
    }

    /**
     * SIEMPRE vía el configurer de Boot: un factory cableado a mano ignoraría
     * {@code spring.rabbitmq.listener.simple.*} (reintento incluido). El {@code recovery-interval} se fija a
     * mano porque Boot no expone esa propiedad; es la misma clave de la que el dispatcher deriva su deadline.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${rabbitmq.listener.recovery-interval-ms:5000}") long recoveryIntervalMillis) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setRecoveryInterval(recoveryIntervalMillis);
        return factory;
    }
}
