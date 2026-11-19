package com.ecomerce.payment.infrastructure.timer;

import com.ecomerce.payment.infrastructure.messaging.outbox.OutboxPurge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La purga de {@code outbox_event}, como recurrente del cronómetro en modo clúster: cada tick lo ejecuta UNA réplica,
 * la que gana su fila en {@code keel_timer}. Lo que borra y cómo (la retención, los lotes, sus transacciones) es
 * lo de {@link OutboxPurge#purge()}, que no cambia: solo cambia quién la dispara.
 */
@Component
public class OutboxEventPurgeTimer implements RecurringTimerHandler {

    private final OutboxPurge outboxPurge;

    @Value("${outbox.purge.cron:0 0 3 * * *}")
    private String cron;

    public OutboxEventPurgeTimer(OutboxPurge outboxPurge) {
        this.outboxPurge = outboxPurge;
    }

    @Override
    public String kind() {
        return TimerKinds.PURGE_OUTBOX_EVENT;
    }

    @Override
    public String spec() {
        return cron;
    }

    @Override
    public void tick() {
        outboxPurge.purge();
    }
}
