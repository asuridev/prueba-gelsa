package com.ecomerce.payment.domain.errors;

/**
 * El cobro no está en authorized, capturing ni captured.
 */
public class PaymentNotCapturableError extends ConflictException {

    public PaymentNotCapturableError(String message) {
        super(message, "PAYMENT_NOT_CAPTURABLE", 409, null);
    }
}
