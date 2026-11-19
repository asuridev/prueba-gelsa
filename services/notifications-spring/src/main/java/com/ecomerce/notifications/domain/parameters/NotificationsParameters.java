package com.ecomerce.notifications.domain.parameters;

/**
 * Parámetros de despliegue de notifications, ya validados.
 *
 * <p>Value object de dominio: llega por constructor a quien lo necesite, nunca por
 * {@code @Value} desde la capa de aplicación. Lo puebla {@link NotificationsParametersConfig}
 * desde {@code notifications.*} de la configuración por perfil.
 *
 * <p>Las cotas se comprueban AQUÍ, al construirlo, y por eso un valor mal puesto tumba el
 * arranque en vez de aparecer en la primera petición que lo use.
 *
 * @param sendingTimeoutMinutes Minutos que un mensaje puede pasar en sending antes de que el rescate lo dé por fallido sin reenviarlo. Mínimo 2, para que el tope de 1 minuto de un intento contra el relay quede siempre por debajo.
 * @param personalDataRetentionMonths Meses de calendario tras requestedAt a partir de los que se purgan los datos personales de un mensaje terminal.
 */
public record NotificationsParameters(Integer sendingTimeoutMinutes, Integer personalDataRetentionMonths) {

    public NotificationsParameters {
        if (sendingTimeoutMinutes == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'sending-timeout-minutes' (variable NOTIFICATIONS_SENDING_TIMEOUT_MINUTES).");
        }
        if (sendingTimeoutMinutes < 2) {
            throw new IllegalStateException("El parámetro 'sending-timeout-minutes' está fuera del rango declarado.");
        }
        if (personalDataRetentionMonths == null) {
            throw new IllegalStateException("Falta el parámetro de despliegue 'personal-data-retention-months' (variable NOTIFICATIONS_PERSONAL_DATA_RETENTION_MONTHS).");
        }
        if (personalDataRetentionMonths < 0) {
            throw new IllegalStateException("El parámetro 'personal-data-retention-months' está fuera del rango declarado.");
        }
    }
}
