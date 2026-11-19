package com.ecomerce.payment.domain.errors;

/**
 * No existe un cobro con ese id.
 */
public class PaymentNotFoundError extends NotFoundException {

    public PaymentNotFoundError(String message) {
        super(message, "PAYMENT_NOT_FOUND", 404, null);
    }
}
