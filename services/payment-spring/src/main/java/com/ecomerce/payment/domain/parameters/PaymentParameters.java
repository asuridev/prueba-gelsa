package com.ecomerce.payment.domain.parameters;

/**
 * Parámetros de despliegue de payment, ya validados.
 *
 * <p>Value object de dominio: llega por constructor a quien lo necesite, nunca por
 * {@code @Value} desde la capa de aplicación. Lo puebla {@link PaymentParametersConfig}
 * desde {@code payment.*} de la configuración por perfil.
 *
 * <p>Las cotas se comprueban AQUÍ, al construirlo, y por eso un valor mal puesto tumba el
 * arranque en vez de aparecer en la primera petición que lo use.
 *
 * @param actionRequiredTimeoutHours Horas que un cobro puede esperar la autenticación del cliente (3DS) antes de que el barrido lo anule.
 */
public record PaymentParameters(Integer actionRequiredTimeoutHours) {

    public PaymentParameters {
        if (actionRequiredTimeoutHours == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'action-required-timeout-hours' (variable PAYMENT_ACTION_REQUIRED_TIMEOUT_HOURS).");
        }
        if (actionRequiredTimeoutHours < 1) {
            throw new IllegalStateException("El parámetro 'action-required-timeout-hours' está fuera del rango declarado.");
        }
    }
}
