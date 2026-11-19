package com.ecomerce.payment.domain.errors;

/**
 * Transición de lifecycle no declarada en el diseño.
 */
public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(String from, String to) {
        super("Transición de estado no permitida: " + from + " -> " + to, "INVALID_STATE_TRANSITION", 409, null);
    }
}
