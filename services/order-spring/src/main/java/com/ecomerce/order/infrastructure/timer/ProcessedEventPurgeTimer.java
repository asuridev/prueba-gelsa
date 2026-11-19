package com.ecomerce.order.infrastructure.timer;

import com.ecomerce.order.infrastructure.messaging.idempotency.IdempotencyGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La purga de {@code processed_event}, como recurrente del cronómetro en modo clúster: cada tick lo ejecuta UNA réplica,
 * la que gana su fila en {@code keel_timer}. Lo que borra y cómo (la retención, los lotes, sus transacciones) es
 * lo de {@link IdempotencyGuard#purge()}, que no cambia: solo cambia quién la dispara.
 */
@Component
public class ProcessedEventPurgeTimer implements RecurringTimerHandler {

    private final IdempotencyGuard idempotencyGuard;

    @Value("${processed-event.purge.cron:0 0 4 * * *}")
    private String cron;

    public ProcessedEventPurgeTimer(IdempotencyGuard idempotencyGuard) {
        this.idempotencyGuard = idempotencyGuard;
    }

    @Override
    public String kind() {
        return TimerKinds.PURGE_PROCESSED_EVENT;
    }

    @Override
    public String spec() {
        return cron;
    }

    @Override
    public void tick() {
        idempotencyGuard.purge();
    }
}
