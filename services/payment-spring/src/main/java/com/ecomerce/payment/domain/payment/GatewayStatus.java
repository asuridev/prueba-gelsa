package com.ecomerce.payment.domain.payment;

/**
 * El estado de un cobro tal como lo ve la pasarela, en un vocabulario que no depende de cuál sea.
 * Cada adaptador traduce a estos valores los estados de la suya.
 */
public enum GatewayStatus {
    /** La pasarela lo tiene, pero todavía no hay desenlace. */
    PENDING,
    /** Espera a que el cliente se autentique (3DS, redirección). */
    ACTION_REQUIRED,
    /** Importe retenido, pendiente de captura. */
    AUTHORIZED,
    /** Cobrado. */
    CAPTURED,
    /** Devuelto, todo o en parte. */
    REFUNDED,
    /** La autorización se anuló o caducó. */
    CANCELED,
    /** El cobro no se hizo. */
    FAILED,
    /** La pasarela no conoce el cobro: la petición no le llegó nunca. */
    NOT_FOUND
}
