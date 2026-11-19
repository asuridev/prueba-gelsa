package com.ecomerce.order.domain.parameters;

/**
 * Parámetros de despliegue de order, ya validados.
 *
 * <p>Value object de dominio: llega por constructor a quien lo necesite, nunca por
 * {@code @Value} desde la capa de aplicación. Lo puebla {@link OrderParametersConfig}
 * desde {@code order.*} de la configuración por perfil.
 *
 * <p>Las cotas se comprueban AQUÍ, al construirlo, y por eso un valor mal puesto tumba el
 * arranque en vez de aparecer en la primera petición que lo use.
 *
 * @param actionRequiredTimeoutHours Horas que un pedido puede seguir en actionRequired antes de que el barrido pregunte a payment por su cobro. Va por encima del plazo del 3DS de payment (24 h) para darle tiempo a publicar el desenlace.
 * @param paymentPendingTimeoutMinutes Minutos que un pedido puede seguir en pendingPayment antes de que el barrido pregunte a payment si registró su cobro; si no lo registró, el pedido pasa a paymentFailed (notReceived).
 * @param paymentActionRetryMinutes Minutos que un pedido puede seguir en authorized sin captura confirmada, o en canceling sin anulación confirmada, antes de que el barrido vuelva a pedir esa acción a payment.
 */
public record OrderParameters(Integer actionRequiredTimeoutHours, Integer paymentPendingTimeoutMinutes, Integer paymentActionRetryMinutes) {

    public OrderParameters {
        if (actionRequiredTimeoutHours == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'action-required-timeout-hours' (variable ORDER_ACTION_REQUIRED_TIMEOUT_HOURS).");
        }
        if (actionRequiredTimeoutHours < 1) {
            throw new IllegalStateException("El parámetro 'action-required-timeout-hours' está fuera del rango declarado.");
        }
        if (paymentPendingTimeoutMinutes == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'payment-pending-timeout-minutes' (variable ORDER_PAYMENT_PENDING_TIMEOUT_MINUTES).");
        }
        if (paymentPendingTimeoutMinutes < 1) {
            throw new IllegalStateException("El parámetro 'payment-pending-timeout-minutes' está fuera del rango declarado.");
        }
        if (paymentActionRetryMinutes == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'payment-action-retry-minutes' (variable ORDER_PAYMENT_ACTION_RETRY_MINUTES).");
        }
        if (paymentActionRetryMinutes < 1) {
            throw new IllegalStateException("El parámetro 'payment-action-retry-minutes' está fuera del rango declarado.");
        }
    }
}
