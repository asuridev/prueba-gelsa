package com.ecomerce.order.domain.valueobject;

import java.util.UUID;

/**
 * Una línea tal como la pide el comprador; el precio no viaja, lo pone el servicio.
 */
public record OrderLineRequest(UUID productId, Integer quantity) {

    public OrderLineRequest {
        if (productId == null) {
            throw new IllegalArgumentException("OrderLineRequest.productId es obligatorio");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("OrderLineRequest.quantity es obligatorio");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("OrderLineRequest.quantity es menor que el mínimo declarado por su tipo (1)");
        }
        if (quantity > 99) {
            throw new IllegalArgumentException("OrderLineRequest.quantity es mayor que el máximo declarado por su tipo (99)");
        }
    }
}
