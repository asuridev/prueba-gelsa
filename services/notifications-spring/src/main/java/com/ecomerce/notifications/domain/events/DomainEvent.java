package com.ecomerce.notifications.domain.events;

/**
 * Marca de evento de dominio: algo que YA ocurrió dentro de un agregado.
 * Los agregados lo acumulan con raise(...) y el adaptador de repositorio lo
 * drena con pullDomainEvents() al persistir.
 */
public interface DomainEvent {

    EventMetadata metadata();
}
