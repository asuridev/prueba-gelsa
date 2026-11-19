package com.ecomerce.payment.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un cobro a pedir a la pasarela.
 *
 * @param reference   la referencia de negocio del cobro (payments.charge.reference: chargeRequestId).
 *                    De ella sale la clave de idempotencia hacia la pasarela, y la pasarela la
 *                    devuelve en cada consulta: es lo que permite reconciliar un cobro sin respuesta
 * @param amount      el importe en unidades mayores (12.50); la conversión es del adaptador
 * @param currency    ISO 4217
 * @param source      con qué se paga
 */
public record ChargeRequest(String reference, BigDecimal amount, String currency, PaymentSource source) {

    public ChargeRequest {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(source, "source");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe de un cobro tiene que ser positivo");
        }
    }
}
