package com.ecomerce.payment.domain.payment;

import java.util.Objects;

/**
 * Un medio de pago guardado en la pasarela, tal como lo describe ella.
 *
 * @param gatewayReference la referencia opaca con la que se cobrará después; no sale nunca del servicio
 * @param brand            la red de la tarjeta tal como la informa la pasarela (visa, mastercard…)
 * @param last4            los cuatro últimos dígitos
 * @param expMonth         mes de caducidad (1-12)
 * @param expYear          año de caducidad
 */
public record SavedPaymentMethod(String gatewayReference, String brand, String last4, int expMonth, int expYear) {

    public SavedPaymentMethod {
        Objects.requireNonNull(gatewayReference, "gatewayReference");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(last4, "last4");
    }
}
