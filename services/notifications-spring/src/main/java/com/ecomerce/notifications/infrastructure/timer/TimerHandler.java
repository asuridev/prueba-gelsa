package com.ecomerce.notifications.infrastructure.timer;

import java.time.Instant;

/**
 * Lo que hace el cronómetro con los temporizadores de un {@code kind}. Corre DENTRO de la transacción en la que el
 * sondeador borra o rearma el temporizador: si lanza, la transacción revierte, el fallo se cuenta y, al agotar los
 * intentos, el temporizador se aparca (T5).
 */
public interface TimerHandler {

    String kind();

    /** {@code dbNow} es la hora de la BASE (T1): la única que compara el cronómetro. */
    TimerOutcome handle(ClaimedTimer timer, Instant dbNow);

    /**
     * ¿Corre dentro de la transacción del sondeador? Un plazo sí. Un trabajo de un lote asíncrono (DSL 2.30) no: procesa
     * un chunk en el que cada elemento abre la suya, y envolverlo en una sola volvería a ser el lote en una transacción.
     * Entonces el sondeador borra o rearma el temporizador DESPUÉS, con la valla, y un fallo cuenta como siempre.
     */
    default boolean inTransaction() {
        return true;
    }
}
