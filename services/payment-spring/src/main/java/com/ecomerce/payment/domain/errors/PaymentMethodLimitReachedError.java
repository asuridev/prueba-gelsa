package com.ecomerce.payment.domain.errors;

/**
 * El titular ya tiene 10 medios active.
 */
public class PaymentMethodLimitReachedError extends BusinessException {

    public PaymentMethodLimitReachedError(String message) {
        super(message, "PAYMENT_METHOD_LIMIT_REACHED", 422, null);
    }
}
