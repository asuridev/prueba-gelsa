package com.ecomerce.order.infrastructure.http;

import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Respuesta wire de payment.capturePayment (contrato del sistema externo).
 */
public record CapturePaymentResponse(UUID id, PaymentStatus status) {

    public CapturePaymentResponse {
        if (id == null) {
            throw new IllegalStateException(
                    "payment.capturePayment: la respuesta no trae 'id', que el contrato declara obligatorio");
        }
        if (status == null) {
            throw new IllegalStateException(
                    "payment.capturePayment: la respuesta no trae 'status', que el contrato declara obligatorio");
        }
    }
}
