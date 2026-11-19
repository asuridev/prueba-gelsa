package com.ecomerce.order.domain.errors;

/**
 * La pasarela rechazó la anulación; el pedido sigue esperando el 3DS y se puede volver a intentar.
 */
public class OrderCancelRejectedError extends BusinessException {

    public OrderCancelRejectedError(String message) {
        super(message, "ORDER_CANCEL_REJECTED", 422, null);
    }
}
