package com.ecomerce.payment.domain.payment;

import java.util.Objects;

/**
 * Con qué se paga, siempre como referencia OPACA de la pasarela. Nunca un dato de tarjeta.
 *
 * @param kind  TOKEN si lo produjo el componente de la pasarela con el cliente delante; SAVED si es
 *              la referencia de un medio guardado (cliente ausente)
 * @param value el token, o la referencia que devolvió {@link com.ecomerce.payment.application.port.out.PaymentGateway#savePaymentMethod}
 */
public record PaymentSource(Kind kind, String value) {

    public enum Kind { TOKEN, SAVED }

    public PaymentSource {
        Objects.requireNonNull(kind, "kind");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Un medio de pago necesita su referencia");
        }
    }

    public static PaymentSource token(String token) {
        return new PaymentSource(Kind.TOKEN, token);
    }

    public static PaymentSource saved(String gatewayReference) {
        return new PaymentSource(Kind.SAVED, gatewayReference);
    }
}
