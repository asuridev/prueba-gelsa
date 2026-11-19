package com.ecomerce.payment.domain.errors;

/**
 * La petición no trae la cabecera Idempotency-Key.
 */
public class IdempotencyKeyRequiredError extends BadRequestException {

    public IdempotencyKeyRequiredError(String message) {
        super(message, "IDEMPOTENCY_KEY_REQUIRED", 400, null);
    }
}
