package com.ecomerce.payment.infrastructure.messaging.events;

import com.ecomerce.payment.application.support.RawJsonDeserializer;
import com.ecomerce.payment.domain.events.EventMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Evento de integración PaymentActionRequired: proyección de wire del evento de dominio
 * PaymentActionRequiredEvent, publicado en el canal 'paymentEvents'.
 *
 * Deliberadamente desacoplado del dominio: cambiar el broker o el formato de
 * serialización no debe alcanzar a domain/events.
 *
 * La metadata se conserva como componente (el bridge la necesita para construir
 * la EventEnvelope) pero NO se serializa: la metadata autoritativa del mensaje
 * es la del envelope, y duplicarla en 'data' confundiría al consumidor.
 */
public record PaymentActionRequiredIntegrationEvent(@JsonIgnore EventMetadata metadata, UUID paymentId, String chargeRequestId, String customerRef, BigDecimal amount, String currency, @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) String customerAction) {
}
