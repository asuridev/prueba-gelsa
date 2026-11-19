package com.ecomerce.notifications.infrastructure.timer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La tabla del cronómetro (`keel_timer`), detrás de un puerto: las sentencias son las de keel-core, iguales en
 * los cuatro generadores, y la hora que comparan es SIEMPRE la de la base.
 */
public interface TimerStore {

    /** La hora de la base. */
    Instant dbNow();

    /** Arma un plazo (o lo rearma si ya existía), en la transacción en curso. */
    void arm(String kind, UUID entityId, Instant dueAt);

    /** Asegura la fila de un recurrente: la crea, o recalcula su vencimiento si su spec cambió (R1). */
    void ensureRecurring(String kind, String spec, Instant dueAt);

    /** Reclama lo vencido y libre de esos kinds, en orden de vencimiento, con arriendo y valla. Confirma antes de volver. */
    List<ClaimedTimer> claim(List<String> kinds, int limit, long leaseMs, UUID token);

    /** Borra un temporizador, solo si sigue siendo de quien lo reclamó. */
    boolean complete(String id, UUID token);

    /** Lo rearma, libre y con los intentos a cero, solo con la valla. */
    boolean reschedule(String id, UUID token, Instant dueAt);

    /** Cuenta un fallo; tras el retraso se reintenta y, al agotar los intentos, se aparca. */
    boolean fail(String id, UUID token, int maxAttempts, long retryDelayMs);

    /** Cuántos milisegundos faltan para lo próximo reclamable de esos kinds (null: nada; puede ser negativo). */
    Long nextDueMs(List<String> kinds);

    /** Los aparcados, de todos los kinds (T5). */
    long parkedCount();

    /** Cuántos milisegundos lleva esperando lo más antiguo reclamable YA de esos kinds (null: nada). El retraso del cronómetro. */
    Long lagMs(List<String> kinds);

    /** La purga de la tabla: vencido hace más de la retención y sin reclamar (R4, R7). */
    int purge(int retentionDays);
}
