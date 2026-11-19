package com.ecomerce.order.infrastructure.timer;

import java.time.Instant;

/** El desenlace de un temporizador: borrarlo, o rearmarlo para otro vencimiento. */
public record TimerOutcome(boolean done, Instant dueAt) {

    /** Cumplido o descartado: se borra. */
    public static TimerOutcome finished() {
        return new TimerOutcome(true, null);
    }

    /** Un recurrente tras su tick, o un plazo que la revalidación aplaza. */
    public static TimerOutcome rescheduleAt(Instant dueAt) {
        return new TimerOutcome(false, dueAt);
    }
}
