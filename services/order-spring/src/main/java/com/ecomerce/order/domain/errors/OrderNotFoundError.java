package com.ecomerce.order.domain.errors;

/**
 * No existe un pedido con ese id del comprador que pregunta.
 */
public class OrderNotFoundError extends NotFoundException {

    public OrderNotFoundError(String message) {
        super(message, "ORDER_NOT_FOUND", 404, null);
    }
}
