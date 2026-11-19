package com.ecomerce.order.domain.errors;

/**
 * El pedido existe pero es de otro comprador.
 */
public class OrderNotOwnedError extends ForbiddenException {

    public OrderNotOwnedError(String message) {
        super(message, "ORDER_NOT_OWNED", 403, null);
    }
}
