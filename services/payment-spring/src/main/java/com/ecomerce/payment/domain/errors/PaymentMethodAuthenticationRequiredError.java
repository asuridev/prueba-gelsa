package com.ecomerce.payment.domain.errors;

/**
 * La pasarela exige autenticar al cliente (3DS) para guardar el medio; el frontend debe completarla al tokenizar y volver a pedir el guardado.
 */
public class PaymentMethodAuthenticationRequiredError extends BusinessException {

    public PaymentMethodAuthenticationRequiredError(String message) {
        super(message, "PAYMENT_METHOD_AUTHENTICATION_REQUIRED", 422, null);
    }
}
