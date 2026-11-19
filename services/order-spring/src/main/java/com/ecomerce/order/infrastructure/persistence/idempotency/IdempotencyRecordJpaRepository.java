package com.ecomerce.order.infrastructure.persistence.idempotency;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecordJpa, IdempotencyRecordJpa.IdempotencyRecordId> {

    /**
     * Purga por lotes (BatchedPurge): el instante de la fila que ocupa esa POSICIÓN entre las que
     * hay que purgar, o vacío si quedan menos. Es la frontera del lote siguiente.
     */
    @Query("select r.expiresAt from IdempotencyRecordJpa r where r.expiresAt < :cutoff order by r.expiresAt asc")
    List<Instant> findPurgeBoundary(@Param("cutoff") Instant cutoff, Pageable position);

    /**
     * Un lote de la purga: lo anterior al corte hasta {@code upTo} incluido, en SU PROPIA
     * transacción —que es lo que hace que cada lote confirme y suelte sus bloqueos—.
     */
    @Modifying
    @Transactional
    @Query("delete from IdempotencyRecordJpa r where r.expiresAt < :cutoff and r.expiresAt <= :upTo")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff, @Param("upTo") Instant upTo);
}
