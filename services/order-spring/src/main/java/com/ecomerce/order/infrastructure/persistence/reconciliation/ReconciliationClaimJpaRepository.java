package com.ecomerce.order.infrastructure.persistence.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReconciliationClaimJpaRepository
        extends JpaRepository<ReconciliationClaimJpa, ReconciliationClaimJpa.ReconciliationClaimId> {

    /**
     * Se lleva un candidato cuyo reclamo YA CADUCÓ. Devuelve 1 si la marca es de esta
     * instancia y 0 si otra la tiene todavía viva — esa comparación en el WHERE es toda la
     * exclusión mutua, y no depende del motor.
     */
    @Modifying
    @Query("update ReconciliationClaimJpa c set c.claimedAt = :now where c.id.activation = :activation and c.id.entityId = :entityId and c.claimedAt <= :expiredBefore")
    int claimIfExpired(@Param("activation") String activation, @Param("entityId") UUID entityId,
            @Param("now") Instant now, @Param("expiredBefore") Instant expiredBefore);

    /**
     * Purga por lotes (BatchedPurge): el instante de la fila que ocupa esa POSICIÓN entre las que
     * hay que purgar, o vacío si quedan menos. Es la frontera del lote siguiente.
     */
    @Query("select c.claimedAt from ReconciliationClaimJpa c where c.claimedAt < :cutoff order by c.claimedAt asc")
    List<Instant> findPurgeBoundary(@Param("cutoff") Instant cutoff, Pageable position);

    /**
     * Un lote de la purga: lo anterior al corte hasta {@code upTo} incluido, en SU PROPIA
     * transacción —que es lo que hace que cada lote confirme y suelte sus bloqueos—.
     */
    @Modifying
    @Transactional
    @Query("delete from ReconciliationClaimJpa c where c.claimedAt < :cutoff and c.claimedAt <= :upTo")
    int deleteClaimedBefore(@Param("cutoff") Instant cutoff, @Param("upTo") Instant upTo);
}
