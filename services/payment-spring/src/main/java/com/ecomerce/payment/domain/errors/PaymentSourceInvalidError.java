package com.ecomerce.payment.domain.errors;

/**
 * No se indica ninguna fuente de pago o se indican las dos.
 */
public class PaymentSourceInvalidError extends BadRequestException {

    public PaymentSourceInvalidError(String message) {
        super(message, "PAYMENT_SOURCE_INVALID", 400, null);
    }
}
