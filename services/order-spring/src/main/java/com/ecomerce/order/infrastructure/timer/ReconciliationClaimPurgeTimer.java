package com.ecomerce.order.infrastructure.timer;

import com.ecomerce.order.infrastructure.persistence.reconciliation.ReconciliationClaimPurge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La purga de {@code reconciliation_claim}, como recurrente del cronómetro en modo clúster: cada tick lo ejecuta UNA réplica,
 * la que gana su fila en {@code keel_timer}. Lo que borra y cómo (la retención, los lotes, sus transacciones) es
 * lo de {@link ReconciliationClaimPurge#purge()}, que no cambia: solo cambia quién la dispara.
 */
@Component
public class ReconciliationClaimPurgeTimer implements RecurringTimerHandler {

    private final ReconciliationClaimPurge reconciliationClaimPurge;

    @Value("${reconciliation.purge.cron:0 45 4 * * *}")
    private String cron;

    public ReconciliationClaimPurgeTimer(ReconciliationClaimPurge reconciliationClaimPurge) {
        this.reconciliationClaimPurge = reconciliationClaimPurge;
    }

    @Override
    public String kind() {
        return TimerKinds.PURGE_RECONCILIATION_CLAIM;
    }

    @Override
    public String spec() {
        return cron;
    }

    @Override
    public void tick() {
        reconciliationClaimPurge.purge();
    }
}
