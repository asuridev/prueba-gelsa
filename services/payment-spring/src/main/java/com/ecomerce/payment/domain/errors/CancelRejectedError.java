package com.ecomerce.payment.domain.errors;

/**
 * La pasarela rechazó la anulación.
 */
public class CancelRejectedError extends BusinessException {

    public CancelRejectedError(String message) {
        super(message, "CANCEL_REJECTED", 422, null);
    }
}
