package com.ecomerce.payment.infrastructure.messaging.events;

import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.events.EventMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de integración PaymentRefunded: proyección de wire del evento de dominio
 * PaymentRefundedEvent, publicado en el canal 'paymentEvents'.
 *
 * Deliberadamente desacoplado del dominio: cambiar el broker o el formato de
 * serialización no debe alcanzar a domain/events.
 *
 * La metadata se conserva como componente (el bridge la necesita para construir
 * la EventEnvelope) pero NO se serializa: la metadata autoritativa del mensaje
 * es la del envelope, y duplicarla en 'data' confundiría al consumidor.
 */
public record PaymentRefundedIntegrationEvent(@JsonIgnore EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, String refundRequestId, BigDecimal refundAmount, BigDecimal refundedAmount, String currency, PaymentStatus status) {
}
