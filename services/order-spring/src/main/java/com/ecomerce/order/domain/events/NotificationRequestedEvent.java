package com.ecomerce.order.domain.events;

import com.ecomerce.order.domain.valueobject.NotificationVariable;
import java.util.List;

/**
 * Encargo de correo a notifications con el desenlace del cobro de un pedido. La firma la fija notifications (INTEGRATION.md 0.3.0, §Suscripciones). recipients lleva el customerEmail del pedido; variables lleva orderId, totalAmount, currency y, en un pago fallido, failureReason. totalAmount se escribe como texto con punto decimal, sin separador de miles y con tantos decimales como la moneda según ISO 4217 (EUR "25.00", JPY "2500", KWD "2.500"). metadata.source es el nombre del cliente máquina de order ante notifications.
 */
public record NotificationRequestedEvent(EventMetadata metadata, String idempotencyKey, String templateCode, List<String> recipients, List<NotificationVariable> variables) implements DomainEvent {

    /** Emisión desde el agregado: estampa la metadata de esta ocurrencia. */
    public static NotificationRequestedEvent of(String idempotencyKey, String templateCode, List<String> recipients, List<NotificationVariable> variables) {
        return new NotificationRequestedEvent(EventMetadata.now("NotificationRequested"), idempotencyKey, templateCode, recipients, variables);
    }
}
