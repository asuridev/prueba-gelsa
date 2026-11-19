package com.ecomerce.payment.infrastructure.timer;

/**
 * El nombre de cada temporizador en {@code keel_timer} (columna {@code kind}). Es contrato con los otros
 * generadores —comparten tabla—, así que sale de keel-core y no se cambia aquí.
 */
public final class TimerKinds {

    /** La purga de la propia tabla: un recurrente en modo clúster (R7). */
    public static final String TIMER_PURGE = "purge:keel_timer";

    /** La purga de {@code outbox_event}: un recurrente en modo clúster. */
    public static final String PURGE_OUTBOX_EVENT = "purge:outbox_event";

    /** La purga de {@code idempotency_record}: un recurrente en modo clúster. */
    public static final String PURGE_IDEMPOTENCY_RECORD = "purge:idempotency_record";

    /** El `schedule` de sweepPendingPayments: un recurrente en modo clúster, con drenaje. */
    public static final String SCHEDULE_SWEEP_PENDING_PAYMENTS = "schedule:sweepPendingPayments";

    /** El `entity_id` de un recurrente: no tiene entidad, y la unicidad (kind, entity_id) necesita un valor. */
    public static final String NIL_ENTITY_ID = "00000000-0000-0000-0000-000000000000";

    private TimerKinds() {
    }
}
