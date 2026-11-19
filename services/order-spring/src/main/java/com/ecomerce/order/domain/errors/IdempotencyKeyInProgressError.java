package com.ecomerce.order.domain.errors;

/**
 * Otra petición del mismo comprador con la misma clave se está procesando a la vez.
 */
public class IdempotencyKeyInProgressError extends ConflictException {

    public IdempotencyKeyInProgressError(String message) {
        super(message, "IDEMPOTENCY_KEY_IN_PROGRESS", 409, null);
    }
}
