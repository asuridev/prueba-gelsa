package com.ecomerce.order.infrastructure.messaging.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpa, UUID> {

    /**
     * Pendientes en orden de llegada que aún no agotaron los reintentos y cuyo
     * backoff ya venció (nextAttemptAt null = elegible ya). Toma un lock de
     * escritura con SKIP LOCKED (el hint de lock timeout -2): con varias réplicas
     * del relay, cada una se lleva un lote disjunto en vez de competir por las
     * mismas filas. El lock se sostiene hasta el fin de la transacción del relay,
     * que es corta: lo que envuelve es la entrega al broker, no una llamada a un
     * proveedor.
     */
    // SKIP LOCKED, dicho como JPA lo dice: el hint -2 es el código de Hibernate para
    // «no esperes por las filas que otro tiene tomadas, sáltalas». Es lo que hace que cada
    // réplica reciba candidatos DISTINTOS en vez de pelearse por la misma página. El lock
    // dura lo que la transacción del reclamo, que termina en cuanto reclama: ninguna
    // llamada externa ocurre dentro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEventJpa o where o.publishedAt is null and o.attempts < :maxAttempts and (o.nextAttemptAt is null or o.nextAttemptAt <= :now) order by o.createdAt asc")
    List<OutboxEventJpa> findPending(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now, Pageable pageable);

    /**
     * Cuántas filas se rindieron: agotaron los reintentos y siguen sin publicar.
     *
     * <p>Es el dato de la única promesa que este mecanismo hace —«ningún evento se
     * pierde»— justo cuando deja de cumplirse. Era derivable desde siempre y nadie lo
     * derivaba: al rendirse solo ocurría una línea de log, y una línea de log no
     * dispara nada. Alimenta la métrica del relay.
     */
    @Query("select count(o) from OutboxEventJpa o where o.publishedAt is null and o.attempts >= :maxAttempts")
    long countDeadLettered(@Param("maxAttempts") int maxAttempts);

    /**
     * Purga por lotes (BatchedPurge): el instante de la fila que ocupa esa POSICIÓN entre las que
     * hay que purgar, o vacío si quedan menos. Es la frontera del lote siguiente.
     */
    @Query("select o.publishedAt from OutboxEventJpa o where o.publishedAt is not null and o.publishedAt < :cutoff order by o.publishedAt asc")
    List<Instant> findPurgeBoundary(@Param("cutoff") Instant cutoff, Pageable position);

    /**
     * Un lote de la purga: lo anterior al corte hasta {@code upTo} incluido, en SU PROPIA
     * transacción —que es lo que hace que cada lote confirme y suelte sus bloqueos—.
     */
    @Modifying
    @Transactional
    @Query("delete from OutboxEventJpa o where o.publishedAt is not null and o.publishedAt < :cutoff and o.publishedAt <= :upTo")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("upTo") Instant upTo);
}
