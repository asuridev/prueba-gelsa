package com.ecomerce.payment.domain.errors;

/**
 * El medio indicado no existe, está retirado o no es del titular (no se distingue cuál).
 */
public class PaymentMethodUnavailableError extends BusinessException {

    public PaymentMethodUnavailableError(String message) {
        super(message, "PAYMENT_METHOD_UNAVAILABLE", 422, null);
    }
}
