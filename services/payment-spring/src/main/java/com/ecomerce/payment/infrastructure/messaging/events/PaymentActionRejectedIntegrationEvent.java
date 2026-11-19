package com.ecomerce.payment.infrastructure.messaging.events;

import com.ecomerce.payment.domain.enums.PaymentActionRejectedAction;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.events.EventMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de integración PaymentActionRejected: proyección de wire del evento de dominio
 * PaymentActionRejectedEvent, publicado en el canal 'paymentEvents'.
 *
 * Deliberadamente desacoplado del dominio: cambiar el broker o el formato de
 * serialización no debe alcanzar a domain/events.
 *
 * La metadata se conserva como componente (el bridge la necesita para construir
 * la EventEnvelope) pero NO se serializa: la metadata autoritativa del mensaje
 * es la del envelope, y duplicarla en 'data' confundiría al consumidor.
 */
public record PaymentActionRejectedIntegrationEvent(@JsonIgnore EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, PaymentActionRejectedAction action, String refundRequestId, BigDecimal refundAmount, PaymentStatus status) {
}
