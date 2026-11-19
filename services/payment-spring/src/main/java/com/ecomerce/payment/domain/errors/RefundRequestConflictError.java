package com.ecomerce.payment.domain.errors;

/**
 * Ya existe una devolución con ese refundRequestId con otro importe o sobre otro cobro.
 */
public class RefundRequestConflictError extends ConflictException {

    public RefundRequestConflictError(String message) {
        super(message, "REFUND_REQUEST_CONFLICT", 409, null);
    }
}
