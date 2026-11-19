package com.ecomerce.order.infrastructure.timer;

import com.ecomerce.order.infrastructure.persistence.idempotency.JpaIdempotencyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La purga de {@code idempotency_record}, como recurrente del cronómetro en modo clúster: cada tick lo ejecuta UNA réplica,
 * la que gana su fila en {@code keel_timer}. Lo que borra y cómo (la retención, los lotes, sus transacciones) es
 * lo de {@link JpaIdempotencyStore#purge()}, que no cambia: solo cambia quién la dispara.
 */
@Component
public class IdempotencyRecordPurgeTimer implements RecurringTimerHandler {

    private final JpaIdempotencyStore jpaIdempotencyStore;

    @Value("${idempotency-record.purge.cron:0 30 4 * * *}")
    private String cron;

    public IdempotencyRecordPurgeTimer(JpaIdempotencyStore jpaIdempotencyStore) {
        this.jpaIdempotencyStore = jpaIdempotencyStore;
    }

    @Override
    public String kind() {
        return TimerKinds.PURGE_IDEMPOTENCY_RECORD;
    }

    @Override
    public String spec() {
        return cron;
    }

    @Override
    public void tick() {
        jpaIdempotencyStore.purge();
    }
}
