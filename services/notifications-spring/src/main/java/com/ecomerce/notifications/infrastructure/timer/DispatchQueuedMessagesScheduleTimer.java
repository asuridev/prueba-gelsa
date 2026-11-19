package com.ecomerce.notifications.infrastructure.timer;

import com.ecomerce.notifications.application.support.SweepPass;
import com.ecomerce.notifications.infrastructure.scheduling.EmailMessageScheduler;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El `schedule` de dispatchQueuedMessages (su cron, {@link #spec()}, en UTC), como recurrente del cronómetro en modo clúster:
 * cada tick lo ejecuta UNA réplica, la que gana su fila en {@code keel_timer}.
 *
 * <p>Por eso el tick DRENA: con una sola réplica trabajando, repite la pasada mientras el lote salga lleno —lo avisa el
 * reclamo con {@link SweepPass#claimed}— y para cuando sale a medias, cuando llega su siguiente tick o cuando agota
 * {@code timer.drain-budget-ms}, que es menor que el arriendo: un tick más largo que su arriendo lo repetiría otra
 * réplica a la vez. Lo que quede pasa al siguiente tick. Sin aviso (un reclamo que no lo da), una pasada por tick.
 *
 * <p>El tick sigue siendo AL MENOS UNA VEZ: si la réplica muere a mitad, otra lo repite al vencer el arriendo. Por eso el
 * barrido sigue reclamando sus filas, aunque ya no corra en todas las réplicas a la vez.
 */
@Component
public class DispatchQueuedMessagesScheduleTimer implements RecurringTimerHandler {

    private final EmailMessageScheduler emailMessageScheduler;

    @Value("${timer.drain-budget-ms:60000}")
    private long drainBudgetMs;

    public DispatchQueuedMessagesScheduleTimer(EmailMessageScheduler emailMessageScheduler) {
        this.emailMessageScheduler = emailMessageScheduler;
    }

    @Override
    public String kind() {
        return TimerKinds.SCHEDULE_DISPATCH_QUEUED_MESSAGES;
    }

    /** El cron del diseño con el segundo repartido (scheduleCron de keel-core): no es configurable. */
    @Override
    public String spec() {
        return "0 * * * * *";
    }

    @Override
    public void tick() {
        Instant start = Instant.now();
        long budget = Math.min(Duration.between(start, TimerPoller.nextTick(spec(), start)).toMillis(), drainBudgetMs);
        for (;;) {
            SweepPass.begin();
            boolean full;
            try {
                emailMessageScheduler.dispatchQueuedMessages();
            } finally {
                full = SweepPass.end();
            }
            if (!full || Duration.between(start, Instant.now()).toMillis() >= budget) {
                return;
            }
        }
    }
}
