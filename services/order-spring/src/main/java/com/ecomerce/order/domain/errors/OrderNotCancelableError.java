package com.ecomerce.order.domain.errors;

/**
 * El pedido no está en actionRequired (ni ya en canceling o canceled).
 */
public class OrderNotCancelableError extends ConflictException {

    public OrderNotCancelableError(String message) {
        super(message, "ORDER_NOT_CANCELABLE", 409, null);
    }
}
