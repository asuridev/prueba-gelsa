package com.ecomerce.payment.domain.errors;

/**
 * El medio no existe, está retirado o no es del titular (no se distingue cuál).
 */
public class PaymentMethodNotFoundError extends NotFoundException {

    public PaymentMethodNotFoundError(String message) {
        super(message, "PAYMENT_METHOD_NOT_FOUND", 404, null);
    }
}
