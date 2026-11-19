package com.ecomerce.payment.infrastructure.payment;

/** Un aviso que no firma la pasarela, o que llega fuera de su ventana. */
public class InvalidPaymentNoticeException extends RuntimeException {

    public InvalidPaymentNoticeException(String message) {
        super(message);
    }
}
