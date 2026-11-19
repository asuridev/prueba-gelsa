package com.ecomerce.order.domain.errors;

/**
 * Dos líneas piden el mismo productId.
 */
public class DuplicateOrderLineError extends BusinessException {

    public DuplicateOrderLineError(String message) {
        super(message, "DUPLICATE_ORDER_LINE", 422, null);
    }
}
