package com.ecomerce.payment.domain.errors;

/**
 * El cobro cambió mientras se procesaba la petición.
 */
public class ConcurrentModificationError extends ConflictException {

    public ConcurrentModificationError(String message) {
        super(message, "CONCURRENT_MODIFICATION", 409, null);
    }
}
