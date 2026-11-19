package com.ecomerce.order.domain.errors;

/**
 * Payment ya no admite anular el cobro porque avanzó (por ejemplo el 3DS se completó a la vez); el pedido vuelve a actionRequired hasta que llegue el desenlace.
 */
public class OrderPaymentAdvancedError extends ConflictException {

    public OrderPaymentAdvancedError(String message) {
        super(message, "ORDER_PAYMENT_ADVANCED", 409, null);
    }
}
