package com.ecomerce.notifications.domain.errors;

/**
 * Algún destinatario tiene una supresión active.
 */
public class RecipientSuppressedError extends BusinessException {

    public RecipientSuppressedError(String message) {
        super(message, "RECIPIENT_SUPPRESSED", 422, null);
    }
}
