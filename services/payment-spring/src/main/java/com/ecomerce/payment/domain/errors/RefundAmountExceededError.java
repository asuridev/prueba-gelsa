package com.ecomerce.payment.domain.errors;

/**
 * amount supera lo que queda por devolver.
 */
public class RefundAmountExceededError extends BusinessException {

    public RefundAmountExceededError(String message) {
        super(message, "REFUND_AMOUNT_EXCEEDED", 422, null);
    }
}
