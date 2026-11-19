package com.ecomerce.order.infrastructure.persistence.repositories;

import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.infrastructure.persistence.entities.OrderJpa;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderJpa, UUID>, JpaSpecificationExecutor<OrderJpa> {

    @EntityGraph(attributePaths = { "lines" })
    @Override
    Optional<OrderJpa> findById(UUID id);

    /**
     * Candidatos de payment.chargeOrder: siguen esperando y llevan más
     * de lo tolerado ahí. El orden es por la marca de espera —el que más lleva, primero—, y
     * quién se los queda lo decide el reclamo, no esta consulta. Los que ya tienen una marca
     * VIVA no son candidatos: sin excluirlos, la cabeza de la cola —reclamada y aún sin
     * resolver— ocuparía el lote de cada pasada hasta caducar, y los de detrás no se
     * reconciliarían antes.
     */
    // SKIP LOCKED, dicho como JPA lo dice: el hint -2 es el código de Hibernate para
    // «no esperes por las filas que otro tiene tomadas, sáltalas». Es lo que hace que cada
    // réplica reciba candidatos DISTINTOS en vez de pelearse por la misma página. El lock
    // dura lo que la transacción del reclamo, que termina en cuanto reclama: ninguna
    // llamada externa ocurre dentro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e.id from OrderJpa e where ((e.status = :pendingState and e.chargeOrderAwaitingSince < :pendingBefore)"
            + " or (e.status = :actionState and e.actionRequiredSince < :actionBefore))"
            + " and not exists (select 1 from ReconciliationClaimJpa c where c.id.activation = :activation and c.id.entityId = e.id and c.claimedAt > :claimExpiredBefore)"
            + " order by (case when e.status = :pendingState then e.chargeOrderAwaitingSince else e.actionRequiredSince end) asc")
    List<UUID> candidatesForReconcileStalledOrdersChargeOrder(@Param("pendingState") OrderStatus pendingState, @Param("pendingBefore") Instant pendingBefore,
            @Param("actionState") OrderStatus actionState, @Param("actionBefore") Instant actionBefore,
            @Param("activation") String activation, @Param("claimExpiredBefore") Instant claimExpiredBefore, Pageable pageable);

    /**
     * Candidatos de payment.captureOrderPayment: siguen esperando y llevan más
     * de lo tolerado ahí. El orden es por la marca de espera —el que más lleva, primero—, y
     * quién se los queda lo decide el reclamo, no esta consulta. Los que ya tienen una marca
     * VIVA no son candidatos: sin excluirlos, la cabeza de la cola —reclamada y aún sin
     * resolver— ocuparía el lote de cada pasada hasta caducar, y los de detrás no se
     * reconciliarían antes.
     */
    // SKIP LOCKED, dicho como JPA lo dice: el hint -2 es el código de Hibernate para
    // «no esperes por las filas que otro tiene tomadas, sáltalas». Es lo que hace que cada
    // réplica reciba candidatos DISTINTOS en vez de pelearse por la misma página. El lock
    // dura lo que la transacción del reclamo, que termina en cuanto reclama: ninguna
    // llamada externa ocurre dentro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e.id from OrderJpa e where e.status in :states and e.captureOrderPaymentAwaitingSince < :staleBefore"
            + " and not exists (select 1 from ReconciliationClaimJpa c where c.id.activation = :activation and c.id.entityId = e.id and c.claimedAt > :claimExpiredBefore)"
            + " order by e.captureOrderPaymentAwaitingSince asc")
    List<UUID> candidatesForReconcileStalledOrdersCaptureOrderPayment(@Param("states") List<OrderStatus> states, @Param("staleBefore") Instant staleBefore,
            @Param("activation") String activation, @Param("claimExpiredBefore") Instant claimExpiredBefore, Pageable pageable);

    /**
     * Candidatos de payment.cancelOrderPayment: siguen esperando y llevan más
     * de lo tolerado ahí. El orden es por la marca de espera —el que más lleva, primero—, y
     * quién se los queda lo decide el reclamo, no esta consulta. Los que ya tienen una marca
     * VIVA no son candidatos: sin excluirlos, la cabeza de la cola —reclamada y aún sin
     * resolver— ocuparía el lote de cada pasada hasta caducar, y los de detrás no se
     * reconciliarían antes.
     */
    // SKIP LOCKED, dicho como JPA lo dice: el hint -2 es el código de Hibernate para
    // «no esperes por las filas que otro tiene tomadas, sáltalas». Es lo que hace que cada
    // réplica reciba candidatos DISTINTOS en vez de pelearse por la misma página. El lock
    // dura lo que la transacción del reclamo, que termina en cuanto reclama: ninguna
    // llamada externa ocurre dentro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e.id from OrderJpa e where e.status in :states and e.cancelOrderPaymentAwaitingSince < :staleBefore"
            + " and not exists (select 1 from ReconciliationClaimJpa c where c.id.activation = :activation and c.id.entityId = e.id and c.claimedAt > :claimExpiredBefore)"
            + " order by e.cancelOrderPaymentAwaitingSince asc")
    List<UUID> candidatesForReconcileStalledOrdersCancelOrderPayment(@Param("states") List<OrderStatus> states, @Param("staleBefore") Instant staleBefore,
            @Param("activation") String activation, @Param("claimExpiredBefore") Instant claimExpiredBefore, Pageable pageable);
}
