package com.ecomerce.order.infrastructure.http;

import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Respuesta wire de payment.cancelPayment (contrato del sistema externo).
 */
public record CancelPaymentResponse(UUID id, PaymentStatus status, PaymentFailureReason failureReason) {

    public CancelPaymentResponse {
        if (id == null) {
            throw new IllegalStateException(
                    "payment.cancelPayment: la respuesta no trae 'id', que el contrato declara obligatorio");
        }
        if (status == null) {
            throw new IllegalStateException(
                    "payment.cancelPayment: la respuesta no trae 'status', que el contrato declara obligatorio");
        }
    }
}
