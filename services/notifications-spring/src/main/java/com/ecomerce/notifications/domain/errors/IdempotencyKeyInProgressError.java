package com.ecomerce.notifications.domain.errors;

/**
 * Otra petición con el mismo (requestedBy, idempotencyKey) se está aceptando a la vez.
 */
public class IdempotencyKeyInProgressError extends ConflictException {

    public IdempotencyKeyInProgressError(String message) {
        super(message, "IDEMPOTENCY_KEY_IN_PROGRESS", 409, null);
    }
}
