package com.ecomerce.payment.domain.errors;

/**
 * El cobro no está en authorized, actionRequired, canceling ni canceled.
 */
public class PaymentNotCancelableError extends ConflictException {

    public PaymentNotCancelableError(String message) {
        super(message, "PAYMENT_NOT_CANCELABLE", 409, null);
    }
}
