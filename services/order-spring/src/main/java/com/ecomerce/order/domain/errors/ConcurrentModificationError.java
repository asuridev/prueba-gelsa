package com.ecomerce.order.domain.errors;

/**
 * Otra escritura cambió el pedido a la vez.
 */
public class ConcurrentModificationError extends ConflictException {

    public ConcurrentModificationError(String message) {
        super(message, "CONCURRENT_MODIFICATION", 409, null);
    }
}
