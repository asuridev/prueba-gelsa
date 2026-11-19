package com.ecomerce.notifications.infrastructure.persistence.repositories;

import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.ecomerce.notifications.infrastructure.persistence.entities.EmailMessageJpa;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface EmailMessageJpaRepository extends JpaRepository<EmailMessageJpa, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<EmailMessageJpa> {

    Optional<EmailMessageJpa> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

    // SKIP LOCKED, dicho como JPA lo dice: el hint -2 es el código de Hibernate para
    // «no esperes por las filas que otro tiene tomadas, sáltalas». Es lo que hace que cada
    // réplica reciba candidatos DISTINTOS en vez de pelearse por la misma página. El lock
    // dura lo que la transacción del reclamo, que termina en cuanto reclama: ninguna
    // llamada externa ocurre dentro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e.id from EmailMessageJpa e where e.status in :states and e.sendingSince < :staleBefore order by e.sendingSince asc")
    List<UUID> candidatesForStalledDispatchQueuedMessages(@Param("states") List<MessageStatus> states, @Param("staleBefore") Instant staleBefore, Pageable pageable);

    /**
     * El reclamo propiamente dicho: renueva {@code sendingSince} SOLO si la fila sigue en sending y sigue
     * atascada; NO cambia el estado, porque la transición a failed es del dominio. Devuelve 1 si esta instancia se la llevó y 0 si otra llegó
     * antes. Esa comparación en el WHERE es toda la exclusión mutua, y no depende del motor.
     */
    @Modifying
    @Query("update EmailMessageJpa e set e.sendingSince = :leasedAt where e.id = :id and e.status in :states and e.sendingSince < :staleBefore")
    int claimForStalledDispatchQueuedMessages(@Param("id") UUID id, @Param("states") List<MessageStatus> states, @Param("staleBefore") Instant staleBefore, @Param("leasedAt") Instant leasedAt);

    /**
     * La guarda de sendQueuedMessage: pasa la fila a sending SOLO si sigue en su estado
     * de partida. Devuelve 1 si esta ejecución se la llevó y 0 si otra llegó antes. Esa
     * comparación en el WHERE es toda la exclusión mutua, y no depende del motor.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailMessageJpa e set e.status = :to, e.sendingSince = :now where e.id = :id and e.status in :states")
    int claimForSendQueuedMessage(@Param("id") UUID id, @Param("states") List<MessageStatus> states, @Param("to") MessageStatus to, @Param("now") Instant now);

    /** Ids de los mensajes en un estado, del más antiguo al más nuevo (desempata el id). */
    @Query("select e.id from EmailMessageJpa e where e.status = :status order by e.requestedAt asc, e.id asc")
    List<UUID> findIdsByStatusOldestFirst(@Param("status") MessageStatus status, Pageable pageable);

    /**
     * Candidatos de la purga: terminales, anteriores al corte y sin purgar. SKIP LOCKED, como el resto de reclamos:
     * una purga solapada no espera por las filas que otra tiene tomadas, las salta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e.id from EmailMessageJpa e where e.status in :states and e.personalDataPurgedAt is null and e.requestedAt < :cutoff order by e.requestedAt asc, e.id asc")
    List<UUID> lockPurgeCandidateIds(@Param("states") List<MessageStatus> states, @Param("cutoff") Instant cutoff, Pageable pageable);
}
