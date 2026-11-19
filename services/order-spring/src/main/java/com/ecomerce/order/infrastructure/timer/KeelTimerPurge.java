package com.ecomerce.order.infrastructure.timer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La purga de la propia tabla del cronómetro (R7): un recurrente más, en modo clúster, así que la ejecuta UNA réplica
 * por tick. Borra lo vencido hace más de la retención y sin reclamar: un temporizador de un kind que ya no conoce
 * ninguna réplica (R4) o uno aparcado que ya se vio.
 */
@Component
public class KeelTimerPurge implements RecurringTimerHandler {

    private final TimerStore store;

    @Value("${timer.purge.cron:0 30 5 * * *}")
    private String cron;

    @Value("${timer.retention-days:7}")
    private int retentionDays;

    public KeelTimerPurge(TimerStore store) {
        this.store = store;
    }

    @Override
    public String kind() {
        return TimerKinds.TIMER_PURGE;
    }

    @Override
    public String spec() {
        return cron;
    }

    @Override
    public void tick() {
        store.purge(retentionDays);
    }
}
