package com.ecomerce.notifications.domain.errors;

/**
 * Otra escritura modificó el remitente entre la lectura y la escritura.
 */
public class ConcurrentModificationError extends ConflictException {

    public ConcurrentModificationError(String message) {
        super(message, "CONCURRENT_MODIFICATION", 409, null);
    }
}
