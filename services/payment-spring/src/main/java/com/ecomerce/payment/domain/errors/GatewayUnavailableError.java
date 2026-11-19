package com.ecomerce.payment.domain.errors;

/**
 * La pasarela no contestó; aquí no se guardó nada y se puede reintentar con un token nuevo.
 */
public class GatewayUnavailableError extends DomainException {

    public GatewayUnavailableError(String message) {
        super(message, "GATEWAY_UNAVAILABLE", 503, null);
    }
}
