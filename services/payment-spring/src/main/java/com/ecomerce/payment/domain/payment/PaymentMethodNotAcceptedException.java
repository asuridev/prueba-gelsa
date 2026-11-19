package com.ecomerce.payment.domain.payment;

/**
 * La pasarela no guardó el medio de pago: lo rechazó (token inválido, caducado o ya usado) o exige que el cliente se
 * autentique (3DS) antes. No es «no contestó»: eso es {@link PaymentGatewayUnavailableException}.
 */
public class PaymentMethodNotAcceptedException extends RuntimeException {

    private final boolean authenticationRequired;

    public PaymentMethodNotAcceptedException(String message, boolean authenticationRequired, Throwable cause) {
        super(message, cause);
        this.authenticationRequired = authenticationRequired;
    }

    /** true si lo que falta es que el cliente complete la autenticación al tokenizar. */
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }
}
