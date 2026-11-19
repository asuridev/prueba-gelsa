package com.ecomerce.order.infrastructure.persistence.reconciliation;

import com.ecomerce.order.infrastructure.persistence.purge.BatchedPurge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Barrido de limpieza de la tabla de reclamos.
 *
 * <p>Corre en todas las réplicas y NO reclama nada, a diferencia del barrido al que sirve:
 * borrar lo caducado es idempotente por forma —borrar dos veces la misma fila da el mismo
 * resultado que borrarla una—, así que solaparse no produce ningún efecto doble.
 *
 * <p>La retención es de días, muy por encima de la caducidad del reclamo (segundos): lo que
 * borra son marcas de encargos ya resueltos. Si borrase una viva, tampoco se rompería nada
 * —el siguiente reclamo la vuelve a insertar—, pero dos réplicas podrían llevarse el mismo
 * candidato en esa pasada.
 */
@Component
public class ReconciliationClaimPurge {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationClaimPurge.class);

    private final ReconciliationClaimJpaRepository repository;

    @Value("${reconciliation.purge.retention-days:7}")
    private int retentionDays;

    @Value("${reconciliation.purge.batch-size:1000}")
    private int purgeBatchSize;

    @Value("${reconciliation.purge.max-batches:500}")
    private int purgeMaxBatches;

    ReconciliationClaimPurge(ReconciliationClaimJpaRepository repository) {
        this.repository = repository;
    }

    public void purge() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = BatchedPurge.run("reconciliation_claim", purgeBatchSize, purgeMaxBatches,
                position -> repository.findPurgeBoundary(cutoff, PageRequest.of(position, 1)),
                upTo -> repository.deleteClaimedBefore(cutoff, upTo), cutoff);
        if (deleted > 0) {
            log.info("Reconciliación: {} reclamos purgados antes de {}", deleted, cutoff);
        }
    }
}
