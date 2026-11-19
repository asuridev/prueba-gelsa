package com.ecomerce.notifications.infrastructure.messaging.outbox;

import com.ecomerce.notifications.infrastructure.persistence.purge.BatchedPurge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Purga lo ya publicado del outbox (lo pendiente no se toca nunca): la tabla no es un histórico.
 *
 * <p>Por lotes y SIN transacción propia: cada lote confirma en la suya (ver BatchedPurge). Una sola transacción
 * alrededor de todo volvería a ser el DELETE único que esto sustituye.
 */
@Component
public class OutboxPurge {

    private static final Logger log = LoggerFactory.getLogger(OutboxPurge.class);

    private final OutboxEventJpaRepository outboxRepository;

    @Value("${outbox.purge.retention-days:7}")
    private int retentionDays;

    @Value("${outbox.purge.batch-size:1000}")
    private int purgeBatchSize;

    @Value("${outbox.purge.max-batches:500}")
    private int purgeMaxBatches;

    public OutboxPurge(OutboxEventJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void purge() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = BatchedPurge.run("Outbox", purgeBatchSize, purgeMaxBatches,
                position -> outboxRepository.findPurgeBoundary(cutoff, PageRequest.of(position, 1)),
                upTo -> outboxRepository.deletePublishedBefore(cutoff, upTo), cutoff);
        if (deleted > 0) {
            log.info("Outbox: purgadas {} filas publicadas antes de {}", deleted, cutoff);
        }
    }
}
