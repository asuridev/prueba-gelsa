package com.ecomerce.payment.domain.errors;

/**
 * La pasarela no acepta el token (inválido, caducado o ya usado) o rechaza el medio.
 */
public class PaymentMethodRejectedError extends BusinessException {

    public PaymentMethodRejectedError(String message) {
        super(message, "PAYMENT_METHOD_REJECTED", 422, null);
    }
}
