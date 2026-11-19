package com.ecomerce.notifications.infrastructure.timer;

import java.time.Instant;

/**
 * Un temporizador recién armado. El sondeador de esta réplica lo escucha DESPUÉS del commit y, si vence antes de lo que
 * pensaba dormir, despierta: así un plazo corto no espera al tope del sueño.
 */
public record TimerArmed(Instant dueAt) {
}
