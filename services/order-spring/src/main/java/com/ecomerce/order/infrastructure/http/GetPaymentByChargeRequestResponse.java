package com.ecomerce.order.infrastructure.http;

import com.ecomerce.order.application.support.RawJsonDeserializer;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Respuesta wire de payment.getPaymentByChargeRequest (contrato del sistema externo).
 */
public record GetPaymentByChargeRequestResponse(UUID id, PaymentStatus status, PaymentFailureReason failureReason, @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) String customerAction) {

    public GetPaymentByChargeRequestResponse {
        if (id == null) {
            throw new IllegalStateException(
                    "payment.getPaymentByChargeRequest: la respuesta no trae 'id', que el contrato declara obligatorio");
        }
        if (status == null) {
            throw new IllegalStateException(
                    "payment.getPaymentByChargeRequest: la respuesta no trae 'status', que el contrato declara obligatorio");
        }
    }
}
