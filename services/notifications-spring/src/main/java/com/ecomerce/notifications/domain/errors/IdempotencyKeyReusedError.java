package com.ecomerce.notifications.domain.errors;

/**
 * Ya existe un mensaje con el mismo (requestedBy, idempotencyKey) y distinto contenido.
 */
public class IdempotencyKeyReusedError extends ConflictException {

    public IdempotencyKeyReusedError(String message) {
        super(message, "IDEMPOTENCY_KEY_REUSED", 409, null);
    }
}
