package com.ecomerce.order.domain.errors;

/**
 * Falta la cabecera Idempotency-Key.
 */
public class IdempotencyKeyRequiredError extends BadRequestException {

    public IdempotencyKeyRequiredError(String message) {
        super(message, "IDEMPOTENCY_KEY_REQUIRED", 400, null);
    }
}
