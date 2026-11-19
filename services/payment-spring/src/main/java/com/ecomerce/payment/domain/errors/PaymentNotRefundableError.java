package com.ecomerce.payment.domain.errors;

/**
 * El cobro no está en captured (incluido el que ya tiene una devolución en curso).
 */
public class PaymentNotRefundableError extends ConflictException {

    public PaymentNotRefundableError(String message) {
        super(message, "PAYMENT_NOT_REFUNDABLE", 409, null);
    }
}
