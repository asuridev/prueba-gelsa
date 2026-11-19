package com.ecomerce.notifications.infrastructure.timer;

/**
 * Un temporizador RECURRENTE en modo clúster: una fila sin entidad, que cada réplica asegura al arrancar (R1) y que en
 * cada tick ejecuta UNA sola réplica, la que la reclama. El siguiente tick lo calcula el sondeador desde la hora de la
 * base de DESPUÉS del tick (R2), con el cron en UTC (T7).
 *
 * <p>El tick corre FUERA de la transacción del sondeador, a diferencia de un plazo: un recurrente (una purga por lotes)
 * gestiona sus propias transacciones, y envolverlo en una sola volvería a ser el DELETE único que la purga por lotes
 * sustituye. Si lanza, el sondeador lo registra y lo rearma para su siguiente tick: un recurrente no se aparca nunca.
 */
public interface RecurringTimerHandler {

    /** El nombre del temporizador en la tabla ({@link TimerKinds}). */
    String kind();

    /** El cron de seis campos (el segundo primero), evaluado en UTC. */
    String spec();

    /** Un tick: lo que hace el mecanismo cada vez que vence. */
    void tick();
}
