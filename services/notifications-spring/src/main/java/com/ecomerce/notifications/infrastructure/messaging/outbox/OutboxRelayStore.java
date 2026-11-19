package com.ecomerce.notifications.infrastructure.messaging.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las tres transacciones CORTAS del relay, separadas a propósito de la publicación al
 * broker (I/O de red).
 *
 * <ol>
 *   <li>{@link #claimBatch}: reclamo con SKIP LOCKED más un LEASE sobre
 *       {@code next_attempt_at}, que retira la fila de {@code findPending} mientras dura el
 *       despacho. Confirma y suelta el lock en cuanto reclama: ninguna llamada de red ocurre
 *       dentro.</li>
 *   <li>{@link #markPublished} / {@link #markFailed}: el desenlace, cada uno en su
 *       transacción, ya sin ningún lock del paso 1.</li>
 * </ol>
 *
 * <p>El lease sustituye a la garantía que el SKIP LOCKED daba gratis mientras la transacción
 * seguía abierta: sin él, otra réplica —o esta misma en la pasada siguiente— volvería a
 * encontrar elegible una fila cuyo despacho sigue en vuelo.
 */
@Component
class OutboxRelayStore {

    private final OutboxEventJpaRepository outboxRepository;

    OutboxRelayStore(OutboxEventJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    List<ClaimedOutboxEvent> claimBatch(int maxAttempts, int batchSize, long claimTimeoutMs) {
        Instant now = Instant.now();
        List<OutboxEventJpa> pending = outboxRepository.findPending(maxAttempts, now, PageRequest.of(0, batchSize));
        Instant leaseUntil = now.plusMillis(claimTimeoutMs);
        List<ClaimedOutboxEvent> claimed = new ArrayList<>(pending.size());
        for (OutboxEventJpa row : pending) {
            claimed.add(new ClaimedOutboxEvent(row.getId(), row.getDestination(), row.getRoutingKey(),
                    row.getEventType(), row.getPayload()));
            // El lease. Si el despacho sale bien, markPublished la saca del todo; si falla,
            // markFailed pisa este valor con el backoff real.
            row.scheduleNextAttempt(leaseUntil);
        }
        return claimed;
    }

    @Transactional
    void markPublished(UUID id) {
        outboxRepository.findById(id).ifPresent(row -> row.markPublished(Instant.now()));
    }

    /**
     * Incrementa el intento, decide dead-letter contra backoff con el contador YA
     * incrementado y aplica el resultado, todo en la misma transacción: el relay nunca
     * vuelve a preguntar «cuántos intentos lleva» con una consulta aparte que podría leer
     * otra cosa.
     */
    @Transactional
    MarkFailedOutcome markFailed(UUID id, String truncatedError, int maxAttempts, long backoffInitialMs, long backoffMaxMs) {
        return outboxRepository.findById(id)
                .map(row -> {
                    row.markFailed(truncatedError);
                    int attempts = row.getAttempts();
                    boolean deadLettered = attempts >= maxAttempts;
                    long nextAttemptAtMs = Long.MAX_VALUE;
                    if (!deadLettered) {
                        // Backoff: aplaza el próximo intento crecientemente para no
                        // reintentar en bucle apretado si el broker está caído.
                        Instant nextAttempt = Instant.now().plusMillis(backoffDelayMs(attempts, backoffInitialMs, backoffMaxMs));
                        row.scheduleNextAttempt(nextAttempt);
                        // Hacia ARRIBA: truncado, la pasada llegaría microsegundos antes de que la fila sea elegible.
                        nextAttemptAtMs = nextAttempt.toEpochMilli() + 1;
                    }
                    return new MarkFailedOutcome(attempts, deadLettered, nextAttemptAtMs);
                })
                // La fila desapareció entre el reclamo y el desenlace: no hay nada que
                // actualizar, y se reporta agotada para no reintentar sobre un id que ya no está.
                .orElse(new MarkFailedOutcome(maxAttempts, true, Long.MAX_VALUE));
    }

    // Backoff exponencial (multiplicador 2) con tope: initial * 2^(attempts-1), saturado en
    // max. En long y con guarda del desplazamiento para no desbordar.
    private static long backoffDelayMs(int attempts, long backoffInitialMs, long backoffMaxMs) {
        int shift = Math.min(attempts - 1, 62);
        long delay = backoffInitialMs << shift;
        if (delay < 0 || delay > backoffMaxMs) {
            return backoffMaxMs;
        }
        return delay;
    }

    /** {@code nextAttemptAtMs}: cuándo vuelve a ser elegible la fila (Long.MAX_VALUE si se rindió); el relay lo recuerda. */
    record MarkFailedOutcome(int attempts, boolean deadLettered, long nextAttemptAtMs) {
    }
}
