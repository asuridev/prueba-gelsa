package com.ecomerce.order.infrastructure.messaging.idempotency;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventJpa, ProcessedEventJpa.ProcessedEventId> {

    /**
     * Purga por lotes (BatchedPurge): el instante de la fila que ocupa esa POSICIÓN entre las que
     * hay que purgar, o vacío si quedan menos. Es la frontera del lote siguiente.
     */
    @Query("select p.processedAt from ProcessedEventJpa p where p.processedAt < :cutoff order by p.processedAt asc")
    List<Instant> findPurgeBoundary(@Param("cutoff") Instant cutoff, Pageable position);

    /**
     * Un lote de la purga: lo anterior al corte hasta {@code upTo} incluido, en SU PROPIA
     * transacción —que es lo que hace que cada lote confirme y suelte sus bloqueos—.
     */
    @Modifying
    @Transactional
    @Query("delete from ProcessedEventJpa p where p.processedAt < :cutoff and p.processedAt <= :upTo")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff, @Param("upTo") Instant upTo);
}
