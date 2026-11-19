package com.ecomerce.order.infrastructure.messaging.outbox;

/**
 * Puerto de salida del outbox: entrega al broker una fila ya serializada.
 *
 * Es lo ÚNICO acoplado al broker en todo el patrón; la implementación vive en
 * infrastructure/messaging y la escribe el agente según keel-stack.json
 * (skill keel-spring-<broker>).
 */
public interface OutboxDispatcher {

    /**
     * Envía el payload al destino indicado. Debe lanzar excepción si la entrega
     * no se confirma: el relay cuenta el intento y reintenta en la pasada siguiente.
     */
    void dispatch(String destination, String routingKey, String eventType, String payload);
}
