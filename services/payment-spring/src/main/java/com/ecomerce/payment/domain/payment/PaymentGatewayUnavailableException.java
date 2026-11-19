package com.ecomerce.payment.domain.payment;

/**
 * La pasarela no contestó, o contestó con un error suyo (5xx, timeout, conexión): NO se sabe si
 * hizo lo que se le pidió. La acción se queda en su estado en vuelo y la resuelve el barrido de
 * reconciliación. Lo que no se hace nunca es repetirla a ciegas: puede cobrar o devolver dos veces.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
