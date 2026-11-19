package com.ecomerce.order.domain.clients;

/**
 * payment contestó con un 4xx de negocio o de validación: no está caído, rechaza la petición.
 *
 * <p>{@code code} es el code del error de payment (PAYMENT_METHOD_UNAVAILABLE, PAYMENT_NOT_FOUND,
 * PAYMENT_NOT_CANCELABLE, CANCEL_REJECTED...) o null si el cuerpo no lo trae. Lo interpreta el
 * caso de uso que llamó; el 401 y el 403 no llegan aquí (son {@link PaymentUnavailableException}).
 */
public class PaymentRejectedException extends RuntimeException {

    private final int status;
    private final String code;

    public PaymentRejectedException(int status, String code, Throwable cause) {
        super("payment rechazó la petición (" + status + (code != null ? " " + code : "") + ")", cause);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
