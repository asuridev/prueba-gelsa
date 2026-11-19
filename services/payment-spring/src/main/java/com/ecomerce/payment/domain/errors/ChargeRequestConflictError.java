package com.ecomerce.payment.domain.errors;

/**
 * Ya existe un cobro con ese chargeRequestId y con otro contenido.
 */
public class ChargeRequestConflictError extends ConflictException {

    public ChargeRequestConflictError(String message) {
        super(message, "CHARGE_REQUEST_CONFLICT", 409, null);
    }
}
