package com.ecomerce.payment.domain.errors;

/**
 * La pasarela rechazó la devolución.
 */
public class RefundRejectedError extends BusinessException {

    public RefundRejectedError(String message) {
        super(message, "REFUND_REJECTED", 422, null);
    }
}
