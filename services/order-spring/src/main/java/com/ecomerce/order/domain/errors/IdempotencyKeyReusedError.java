package com.ecomerce.order.domain.errors;

/**
 * El comprador ya usó esa clave con un contenido distinto.
 */
public class IdempotencyKeyReusedError extends ConflictException {

    public IdempotencyKeyReusedError(String message) {
        super(message, "IDEMPOTENCY_KEY_REUSED", 409, null);
    }
}
